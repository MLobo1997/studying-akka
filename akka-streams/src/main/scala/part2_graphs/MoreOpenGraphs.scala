package part2_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{
  Broadcast,
  Flow,
  GraphDSL,
  RunnableGraph,
  Sink,
  Source,
  ZipWith
}
import akka.stream.{ClosedShape, FanOutShape2, UniformFanInShape}

import java.util.Date
import scala.math.max

object MoreOpenGraphs extends App {
  implicit val system: ActorSystem = ActorSystem()

  /*
  Max3 opeator
   */

  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    val max1 = builder.add(ZipWith[Int, Int, Int](max))
    val max2 = builder.add(ZipWith[Int, Int, Int](max))

    max1.out ~> max2.in0

    //It's uniform cuz it'' all from the same shape
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).map(_ => 5))
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach[Int](x => println(s"Max is: $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      val max3Shape = builder.add(max3StaticGraph)

      source1 ~> max3Shape
      source2 ~> max3Shape
      source3 ~> max3Shape

      max3Shape ~> maxSink

      ClosedShape
    }
  )

  //max3RunnableGraph.run()

  /**
    * Non uniform fan out shape
    */

  case class Transaction(
      id: String,
      source: String,
      recipient: String,
      amount: Int,
      date: Date
  )

  val transactionSource = Source(
    List(
      Transaction("15451989", "Paul", "Jim", 100, new Date),
      Transaction("917589375198", "Jim", "Alice", 7000, new Date),
      Transaction("1824589165", "Daniel", "Jim", 100060, new Date)
    )
  )

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService =
    Sink.foreach[String](id => println(s"Suspicious transaction ID: $id"))

  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTrxFilter =
      builder.add(Flow[Transaction].filter(p => p.amount >= 10000))
    val trxIdExtractor = builder.add(Flow[Transaction].map[String](_.id))

    broadcast.out(1) ~>
      suspiciousTrxFilter ~>
      trxIdExtractor

    new FanOutShape2[Transaction, Transaction, String](
      broadcast.in,
      broadcast.out(0),
      trxIdExtractor.out
    )
  }

  val suspiciousTrxRunnableGraph = RunnableGraph.fromGraph(GraphDSL.create() {
    implicit builder =>
      val suspiciousTrxDetectorShape = builder.add(suspiciousTxnStaticGraph)

      transactionSource ~> suspiciousTrxDetectorShape.in
      suspiciousTrxDetectorShape.out0 ~> bankProcessor
      suspiciousTrxDetectorShape.out1 ~> suspiciousAnalysisService

      ClosedShape
  })

  suspiciousTrxRunnableGraph.run()

  Thread.sleep(100)
  system.terminate()
}

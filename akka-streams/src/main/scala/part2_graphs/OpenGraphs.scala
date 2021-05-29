package part2_graphs

import akka.actor.ActorSystem
import akka.stream.{FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{
  Broadcast,
  Concat,
  Flow,
  GraphDSL,
  Sink,
  Source,
  Zip
}
import GraphDSL.Implicits._

object OpenGraphs extends App {
  implicit val system: ActorSystem = ActorSystem("OpenGraphs")

  /*
  A composite source that concatenates 2 sources
  1 - emits all elements from the first sourcve
  2-  then all from the 2nd
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      val concat = builder.add(Concat[Int](2))

      firstSource ~> concat
      secondSource ~> concat

      SourceShape(concat.out)
    }
  )

  sourceGraph.to(Sink.foreach[Int](println))

  val sink1 = Sink.foreach[Int](x => println(s"Sink 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Sink 2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      val broadcast = builder.add(Broadcast[Int](2))

      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

  //sourceGraph.runWith(sinkGraph)

  val incrementerFlow = Flow[Int].map(_ + 1)
  val multiplierFlow = Flow[Int].map(_ * 10)

  val myFlow = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      /*
      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(Zip[Int, Int]())

      broadcast ~> incrementerFlow ~> zip.in0
      broadcast ~> multiplierFlow ~> zip.in1

      FlowShape(broadcast.in, zip.out)
       */
      val incrementerShape = builder.add(incrementerFlow)
      val multiplierShape = builder.add(multiplierFlow)

      incrementerShape ~> multiplierShape

      FlowShape(incrementerShape.in, multiplierShape.out)
    }
  )

  myFlow.runWith(Source(1 to 20), Sink.foreach[Int](println))

  //sink & source flow
  def fromSinkAndSource[A, B](
      sink: Sink[A, _],
      source: Source[B, _]
  ): Flow[A, B, _] = {
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        val sourceShape = builder.add(source)
        val sinkShape = builder.add(sink)

        FlowShape(sinkShape.in, sourceShape.out)
      }
    )
  }

  //if sink stops, source has no way of knowing.
  val f = Flow.fromSinkAndSource(Sink.foreach[Int](println), Source(1 to 10))

  //that's why this methods exists, which allows for backpressure signaling
  Flow.fromSinkAndSourceCoupled(Sink.foreach[Int](println), Source(1 to 10))

  Thread.sleep(1000)

  system.terminate()
}

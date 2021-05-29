package part2_graphs

import akka.actor.ActorSystem
import akka.stream.{FlowShape, SinkShape}
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {
  implicit val system: ActorSystem = ActorSystem("GMV")

  val wordSource = Source(List("akka", "is", "awesome", "rock", "the", "jvm"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

  /*
  A composite component
  -prints ou all string which are lowercase
  -counts the string that are short (< 5 chars) <-we want to exposed this
   */

  val complexWordSink: Sink[String, Future[Int]] =
    Sink.fromGraph(
      GraphDSL.create(printer, counter)(
        (
            _,
            counterMatValue
        ) => //this composes the materialized values of the inputted components
          counterMatValue
      ) { implicit builder => (printerShape, counterShape) =>
        val broadcast = builder.add(Broadcast[String](2))
        val lowercaseFilterShape =
          builder.add(Flow[String].filter(word => !word.exists(_.isUpper)))
        val lengthFilterShape =
          builder.add(Flow[String].filter(word => word.length < 5))

        broadcast.out(0) ~>
          lowercaseFilterShape ~>
          printerShape
        broadcast.out(1) ~>
          lengthFilterShape ~>
          counterShape

        SinkShape(broadcast.in)
      }
    )

  /*
  val shortStringsCountFuture =
    wordSource.toMat(complexWordSink)(Keep.right).run()
  shortStringsCountFuture.onComplete {
    case Success(count) =>
      println(s"The total nr of short strings is: $count")
    case Failure(ex) =>
      println(s"Failed due to $ex")
  }
   */

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink = Sink.fold[Int, B](0)((count, _) => count + 1)

    Flow.fromGraph(GraphDSL.create(counterSink) {
      implicit builder => counterShape =>
        val givenFlowShape = builder.add(flow)
        val broadcast = builder.add(Broadcast[B](2))

        givenFlowShape ~> broadcast.in
        broadcast.out(0) ~> counterShape

        FlowShape(givenFlowShape.in, broadcast.out(1))
    })
  }

  val simpleFlow = Flow[String].map(_.toUpperCase())
  val r: Future[Int] =
    wordSource.viaMat(enhanceFlow(simpleFlow))(Keep.right).to(printer).run()

  r.onComplete {
    case Success(count) =>
      println(s"The total nr of inputs is: $count")
    case Failure(ex) =>
      println(s"Failed due to $ex")
  }

}

package part1_primer

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object Backpressure extends App {

  /**
    * Fundamnetal feature of reactive streams
    *
    * elements flow as response of demand from consumers
    *
    * consumers send signal to producer to slow down
    *
    * This is all transparent
    */
  implicit val system: ActorSystem = ActorSystem("BackpressureBasics")

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  //fastSource.to(slowSing).run() //this is not backpressured cuz it's fused

  //fastSource.async.to(slowSink).run()

  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x + 1
  }

  //fastSource.async.via(simpleFlow).async.to(slowSink).run()

  /**
    * reactions to backpressure:
    * 1 - slow down if possible (case of source it''s'' not possible
    * 2 - buffer elements until there'' more demand''
    * 3 - drop down elements from the buffer if it overflows
    * 4- tear down/kill the whole stream (failure)
    */

  val bufferedFlow =
    simpleFlow.buffer(
      10,
      overflowStrategy = OverflowStrategy.dropHead
    ) //drops the oldest element from the buffer to make room to the new one //drops the oldest element from the buffer to make room to the new one
  fastSource.async
    .via(bufferedFlow)
    .async
    .to(slowSink)
    .run()

  /*
  overflow strategies:
    drop tail - newest element
    drop new  - drops the element to be added
    drop the entire buffer
    backpressure signal
    fail
   */

  //manually enabling backpressure
  fastSource
    .throttle(2, 1 second)
    .runWith(Sink.foreach[Int](println)) //emit at most 2 elements per second
}

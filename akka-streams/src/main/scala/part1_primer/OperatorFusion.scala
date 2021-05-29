package part1_primer

import akka.actor.{Actor, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.ExecutionContext.Implicits.global

object OperatorFusion extends App {
  implicit val system: ActorSystem = ActorSystem("OperatorFusion")

  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

  //This runs on the SAME ACTOR - operator/component fusion -- it improves throughput
  //simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()

  //this  is equivalent to:
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case x: Int =>
        val x2 = x + 1
        val y = x2 * 10

        println(y)
    }
  }

  //val actor = system.actorOf(Props[SimpleActor])
  //(1 to 1000).foreach(actor ! _)

  ////UPUPUP
  //So a single cpu thread will be used for this
  //This operator fusion is good when the operations are quick
  //but bad when are expensive

  //complex flows
  val complexFlow1 = Flow[Int].map { x =>
    Thread.sleep(1000)
    x + 1
  }

  val complexFlow2 = Flow[Int].map { x =>
    Thread.sleep(1000)
    x * 10
  }

  /*
  simpleSource
    .via(complexFlow1)
    .via(complexFlow2)
    .to(simpleSink)
    .run() // veryyyyy slow!
   */

  //so if you want parallelism you use
  //async boundary

  val r = simpleSource
    .viaMat(complexFlow1)(Keep.right)
    .async //runs on one actor //async boundaries include everything from previous boundary
    .viaMat(complexFlow2)(Keep.right)
    .async //runs in 2nd actor
    .runWith(simpleSink) //runs in 3rd actor

  //ordering guarantees
  Source(1 to 3)
    .map(element => { println(s"Flow A: $element"); element })
    .async
    .map(element => { println(s"Flow B: $element"); element })
    .async
    .map(element => { println(s"Flow C: $element"); element })
    .async
    .runWith(Sink.ignore)

  r.onComplete(_ => system.terminate())
}

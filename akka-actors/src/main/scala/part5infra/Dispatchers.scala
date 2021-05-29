package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object Dispatchers extends App {
  //Dispatchers are responsible for delivering messages

  class Counter extends Actor with ActorLogging {
    var count = 0
    def receive: Receive = {
      case message =>
        count += 1
        log.info(s"${message.toString}: $count")
    }

  }

  val system = ActorSystem("DispatcherDemo")

  //method 1 - programatically
  /*
  val actors =
    for (i <- 1 to 10)
      yield system.actorOf(
        Props[Counter].withDispatcher("my-dispatcher"),
        s"counter_$i"
      )

  val r = new Random()
  for (i <- 1 to 100) {
    actors(r.nextInt(10)) ! i
  }
   */

  // method #2 - from config
  val rtjvmActor = system.actorOf(Props[Counter], "rtjvm")

  /**
    * Dispatcher implement ExecutionContext
    */

  class DBActor extends Actor with ActorLogging {
    //implicit val executionContext: ExecutionContext = context.dispatcher

    //solution 1
    implicit val executionContext: ExecutionContext =
      context.system.dispatchers.lookup(
        "my-dispatcher"
      ) //if you use a different dispatcher that the one used by your actors that's alright

    //solution 2

    def receive: Receive = {
      case msg =>
        Future { //this future uses the executionDispatcher implicit
          //not recomended to use futures, but some cases might be necessary
          Thread.sleep(5000)
          log.info(s"Success: $msg")
        }
    }
  }

  val DBActor = system.actorOf(Props[DBActor])
  DBActor ! "the meaning of life is 42"

  val nonblockingActor = system.actorOf(Props[Counter])
  for (i <- 1 to 1000) {
    val msg = s"Message $i"
    DBActor ! msg
    nonblockingActor ! msg
  }
}

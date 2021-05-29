package part2actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}

object ActorLogging extends App {

  //1 explicit logging
  class ActorWithExplicitLogger extends Actor {
    val logger: LoggingAdapter = Logging(context.system, this)
    def receive: Receive = {
      case message =>
        logger.info(message.toString)
    }
  }

  val system = ActorSystem("LoggingDemo")
  val actor = system.actorOf(Props[ActorWithExplicitLogger])

  actor ! "nanana"

  // 2 actor logging
  class ActorWithLogging extends Actor with ActorLogging {
    def receive: Receive = {
      case (a, b) => log.info("a: {} b: {}", a, b)
      case msg    => log.info(msg.toString)
    }
  }
  val simplerActor = system.actorOf(Props[ActorWithLogging])
  simplerActor ! "nanananan"

  //logger is actually an actor //therefore done asynchronously
  //by standard it uses slf4j
}

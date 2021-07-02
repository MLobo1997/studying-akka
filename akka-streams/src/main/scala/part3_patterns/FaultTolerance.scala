package part3_patterns

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, Attributes, RestartSettings}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

object FaultTolerance extends App {
  implicit val system: ActorSystem = ActorSystem("FaultTolerance")

  // 1 - logging
  val faultySource = {
    Source(1 to 10).map(e => if (e == 6) throw new RuntimeException else e)
  } //downstream nodes are informed of the exception
  faultySource.log("trackingElements").to(Sink.ignore) //.run()

  //2 - graceful termination
  faultySource
    .recover {
      case _: RuntimeException => Int.MinValue
    }
    .log("gracefulSource")
    .to(Sink.ignore)
  //.run()

  //3 - recover with another stream

  faultySource
    .recoverWithRetries(
      3,
      {
        case _: RuntimeException => Source(90 to 99)
      }
    )
    .log("recoverWithRetries")
    .to(Sink.ignore)
  //.run()

  //4 backoff supervision

  val restartSource = RestartSource.onFailuresWithBackoff(
    RestartSettings(
      minBackoff = 1 second,
      maxBackoff = 30 seconds,
      randomFactor = 0.2
    )
  )(() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(elem =>
      if (elem == randomNumber) throw new RuntimeException else elem
    )
  })
  restartSource
    .log("restartBackoff")
    .to(Sink.ignore)
  //.run()

  //5 supervision strategy
  val numbers =
    Source(1 to 20)
      .map(e => if (e == 13) throw new RuntimeException else e)
      .log("supervision")
  val supNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy {
    case _: RuntimeException => Resume
    case _                   => Stop
  })

  supNumbers.to(Sink.ignore).run()
}

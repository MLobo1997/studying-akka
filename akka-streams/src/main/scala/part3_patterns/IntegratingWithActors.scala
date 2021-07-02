package part3_patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.Done
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object IntegratingWithActors extends App {
  implicit val system: ActorSystem = ActorSystem("actors")

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a string: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a nr: $n")
        sender() ! 2 * n
      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  val numbersSource = Source(1 to 10)

  //actor as a flow
  implicit val timeout: Timeout = Timeout(2 seconds)
  val actorBasedFlow =
    Flow[Int].ask[Int](parallelism = 4)(
      simpleActor
    ) //4 asks can be occuring simultaneously

  //numbersSource.via(actorBasedFlow).to(Sink.foreach[Int](println)).run()
  //numbersSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.foreach[Int](println)).run()

  /**
    * Actor as a source
    */
  val actorPoweredSource = Source.actorRef(
    completionMatcher = {
      case Done =>
        CompletionStrategy.immediately
    },
    failureMatcher = PartialFunction.empty,
    bufferSize = 10,
    overflowStrategy = OverflowStrategy.dropHead
  )

  val materializedActorRef = actorPoweredSource
    .to(Sink.foreach[Int](nr => println(s"Actor powered flow got nr: $nr")))
    .run()

  materializedActorRef ! 10
  materializedActorRef ! Done

  /*
  Actor as a sink
    -init message
    -ack message to confirm reception
    - complete message
    - exception handle
   */

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream complete")
        context.stop(self)
      case StreamFail(ex) =>
        log.info(s"Stream failed: $ex")
      case message =>
        log.info(s"Message $message received")
        sender() ! StreamAck // lack of stream acknowledge will be interpreted as backpressure
    }
  }

  val destinationActor =
    system.actorOf(Props[DestinationActor], "destinationActor")

  val actorPoweredSink = Sink.actorRefWithBackpressure(
    ref = destinationActor,
    onInitMessage = StreamInit,
    ackMessage = StreamAck,
    onCompleteMessage = StreamComplete,
    onFailureMessage = StreamFail
  )

  Source(1 to 10).to(actorPoweredSink).run()
}

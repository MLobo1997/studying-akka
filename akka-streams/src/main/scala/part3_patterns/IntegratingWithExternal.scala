package part3_patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object IntegratingWithExternal extends App {
  implicit val system: ActorSystem = ActorSystem("IntegratingExt")
  //import system.dispatcher //not recommended for mapAsync
  implicit val dispatcher: MessageDispatcher =
    system.dispatchers.lookup("dedicated-dispatcher")

  def genericExtService[A, B](element: A): Future[B] = ???

  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(
    List(
      PagerEvent("AkkaInfre", "broke", new Date),
      PagerEvent("AkkaInfre2", "brok2e", new Date),
      PagerEvent("AkkaInfre", "broke3", new Date),
      PagerEvent("AkkaInfre4", "broke4", new Date)
    )
  )

  object PagerService {
    private val engineers = List("Daniel", "john", "jake")
    private val emails = Map(
      "Daniel" -> "123@123.com",
      "john" -> "456@123.com",
      "jake" -> "789@123.com"
    )

    def processEvent(pagerEvent: PagerEvent): Future[String] =
      Future {
        val engineeringIndex =
          (pagerEvent.date.toInstant.getEpochSecond / (24 * 60 * 60)) % engineers.length
        val engineer = engineers(engineeringIndex.toInt)
        val engineerEmail = emails(engineer)

        println(
          s"Sending the engineer $engineerEmail a high priority notification: $pagerEvent"
        )
        Thread.sleep(1000)

        engineerEmail
      }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfre")
  val pagedEngineerEmails =
    infraEvents.mapAsync(parallelism = 2)(
      PagerService.processEvent
    ) //determines how many futures can run at the same time
  //guarantees the relative order of elements

  val pagedEmailsSink =
    Sink.foreach[String](email => println(s"Successfully notified $email"))

  pagedEngineerEmails.to(pagedEmailsSink).run()

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Daniel", "john", "jake")
    private val emails = Map(
      "Daniel" -> "123@123.com",
      "john" -> "456@123.com",
      "jake" -> "789@123.com"
    )

    private def processEvent(pagerEvent: PagerEvent): String = {
      val engineeringIndex =
        (pagerEvent.date.toInstant.getEpochSecond / (24 * 60 * 60)) % engineers.length
      val engineer = engineers(engineeringIndex.toInt)
      val engineerEmail = emails(engineer)

      println(
        s"Sending the engineer $engineerEmail a high priority notification: $pagerEvent"
      )
      Thread.sleep(1000)

      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }

  }

  val pagerActor = system.actorOf(Props[PagerActor])
  implicit val timeout: Timeout = Timeout(3 seconds)
  val alternativePagedEngineerEmails =
    infraEvents.mapAsync(parallelism = 4)(event =>
      (pagerActor ? event).mapTo[String]
    )
  alternativePagedEngineerEmails.to(pagedEmailsSink).run()
}

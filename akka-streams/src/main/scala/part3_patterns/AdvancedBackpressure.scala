package part3_patterns

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import java.util.Date
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object AdvancedBackpressure extends App {
  implicit val system: ActorSystem = ActorSystem("AdvBack")

  val controlledFlow =
    Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val eventsSource = Source(
    List(
      PagerEvent("Fail 1", new Date),
      PagerEvent("Fail 2", new Date),
      PagerEvent("Fail 3", new Date),
      PagerEvent("Fail 4", new Date)
    )
  )

  val oncallEngineer = "Daniel"

  def sendEmail(notification: Notification): Unit =
    println(s"Emailing ${notification.email}: ${notification.pagerEvent}")

  val notificationSink =
    Flow[PagerEvent]
      .map(event => Notification(oncallEngineer, event))
      .to(Sink.foreach[Notification](sendEmail))

  //eventsSource.to(notificationSink).run()

  //non-backpressurable source
  def sendEmailSlow(notification: Notification): Unit = {
    Thread.sleep(1000)
    println(s"Emailing ${notification.email}: ${notification.pagerEvent}")
  }

  //for a scenario where you need something to reach the stream ASAP and can't wait for stalled backpressure
  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((event1, event2) => {
      val nInstances = event1.nInstances + event2.nInstances
      PagerEvent(
        s"You have $nInstances events that require your attention",
        new Date,
        nInstances
      )
    })
    .map(resultingEvent => Notification(oncallEngineer, resultingEvent))

  eventsSource
    .via(aggregateNotificationFlow)
    .async
    .to(Sink.foreach[Notification](sendEmailSlow))
    .run()

  /*
  Good for slow producers: extrapolate/expand
   */
  val slowCounter = Source(LazyList.from(1)).throttle(1, 1 second)
  val hungrySink = Sink.foreach[Int](println)

  val extrapolater = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))

  //slowCounter.via(repeater).to(hungrySink).run()

  val expander = Flow[Int].expand(element => Iterator.from(element))
  //extrapolate expands only when there is unmet demand, while the expander does it at all times
  system.terminate()
}

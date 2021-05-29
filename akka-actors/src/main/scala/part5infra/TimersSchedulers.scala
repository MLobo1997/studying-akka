package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props, Timers}

import scala.concurrent.duration.DurationInt

object TimersSchedulers extends App {
  class SimpleActor extends Actor with ActorLogging {
    def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("SchedulerTimersDemo")
  val simpleActor = system.actorOf(Props[SimpleActor])

  system.log.info("Scheduling reminder for simpleActor")

  import system.dispatcher
  system.scheduler.scheduleOnce(1 second) {
    simpleActor ! "remider"
  } //(system.dispatcher) //an execution context for the scheduler to run

  val routine: Cancellable = system.scheduler.scheduleWithFixedDelay(
    1 second,
    2 seconds,
    simpleActor,
    "heartbeat"
  )

  system.scheduler.scheduleOnce(5 seconds) {
    routine.cancel()
  }

  class SelfClosingActor extends Actor with ActorLogging {
    override def postStop(): Unit = {
      log.info("Bye bye")
      super.postStop()
    }

    def receive: Receive = {
      case message =>
        log.info(s"Received: ${message.toString}. Timer started")
        context.become(selfClose(scheduleDeath()))
    }

    def selfClose(cancellable: Cancellable): Receive = {
      case message =>
        log.info(s"Received: ${message.toString}. Resetting timer")
        cancellable.cancel()
        context.become(selfClose(scheduleDeath()))
    }

    private def scheduleDeath(): Cancellable =
      context.system.scheduler.scheduleOnce(1 second) {
        context.stop(self)
      }
  }

  Thread.sleep(1000)

  val selfClosingActor = system.actorOf(Props[SelfClosingActor], "selfCloser")

  selfClosingActor ! "Go"
  selfClosingActor ! "2"
  system.scheduler.scheduleOnce(500 millis, selfClosingActor, "3")
  system.scheduler.scheduleOnce(700 millis, selfClosingActor, "4")
  system.scheduler.scheduleOnce(3000 millis, selfClosingActor, "5")

  /**
    * Timers are a safe way to schedule messages to self.
    */

  case object TimerKey //identifier of the timer
  case object Start
  case object Reminder
  case object Stop
  class TimerBasedHeartBeatActor extends Actor with ActorLogging with Timers {
    timers.startSingleTimer(TimerKey, Start, 500 millis)
    def receive: Receive = {
      case Start =>
        log.info("Started!!!!")
        timers.startTimerWithFixedDelay(
          TimerKey,
          Reminder,
          1 second
        ) //previous timer gets canceled!
      case Reminder =>
        log.info("Reminder!!")
      case Stop =>
        log.warning("Stopping")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  val timerBasedHeartBeatActor =
    system.actorOf(Props[TimerBasedHeartBeatActor], "timerBased")
  system.scheduler.scheduleOnce(5 seconds) {
    timerBasedHeartBeatActor ! Stop
  }

  Thread.sleep(10000)
  system.terminate()
}

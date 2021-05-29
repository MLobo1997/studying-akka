package part4faulttolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import part4faulttolerance.ActorLifecycle.LifecycleActor.StartChild

object ActorLifecycle extends App {

  /**
    * Actor instance
    *  has methods
    *  may have internatial state
    *
    * Actor ref
    *  created with actoroF
    *  has mailbox
    *  contains 1 actor instance
    *  contains UUID
    *
    * Actor path
    *  may or may not have an ActorRef inside
    *
    *  Actors can be
    *    started
    *    suspended
    *    resumed
    *    restarted =
    *      1 - suspended
    *      2 - Swaps the actor instance (resets the state of the actor)
    *      3 - resume
    *    stopped = killed
    *
    */

  class LifecycleActor extends Actor with ActorLogging {

    override def preStart(): Unit = log.info("I am starting")

    override def postStop(): Unit = log.info("I am stopping")

    override def receive: Receive = {
      case StartChild =>
        context.actorOf(Props[LifecycleActor], "child")
    }
  }

  object LifecycleActor {
    object StartChild
  }

  val system = ActorSystem("LifecycleDemo")
  //val parent = system.actorOf(Props[LifecycleActor], "parent")
  //parent ! StartChild
  //parent ! PoisonPill //first child dies then parent dies

  /**
    * restart
    */
  class Parent extends Actor with ActorLogging {
    private val child = context.actorOf(Props[Child], "supersidedChild")
    override def preStart(): Unit = log.info("I am starting")

    override def postStop(): Unit = log.info("I am stopping")
    def receive: Receive = {
      case FailChild =>
        child ! Fail
    }
  }
  object Fail
  object FailChild
  object CheckChild
  object Check

  class Child extends Actor with ActorLogging {
    override def preStart(): Unit = log.info("I am starting")

    override def postStop(): Unit = log.info("I am stopping")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.info(s"Supervised actor restarting because of ${reason.getMessage}")

    override def postRestart(reason: Throwable): Unit = {
      log.info("Supervised actor restarted")
    }

    def receive: Receive = {
      case Fail =>
        log.warning("Child will fail now")
        throw new RuntimeException(
          "I failed"
        ) //Restart occurs when there is an exception
    }
  }

  val supervisor = system.actorOf(Props[Parent], "supervisor")
  supervisor ! FailChild

  //actors that trhow exception remain alive. This is called the default supervision strategy

  system.terminate()
}

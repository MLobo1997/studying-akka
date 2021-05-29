package part4faulttolerance

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{
  Actor,
  ActorRef,
  ActorSystem,
  AllForOneStrategy,
  OneForOneStrategy,
  Props,
  SupervisorStrategy,
  Terminated
}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import part4faulttolerance.SupervisionSpec.{
  AllForOneSupervisor,
  FussyWordCounter,
  NoDeathOnRestartSupervisor,
  Report,
  Supervisor
}

class SupervisionSpec
    extends TestKit(ActorSystem("SupervisionSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with BeforeAndAfterAll {
  //Akka filosofi - if actor fails, it's ok. It's up to parent to resolve

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A supervisor" should {
    "resume its child in case of a minor fault" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I Love akka"
      child ! Report
      expectMsg(3)

      child ! "asndfjasnfjksanfjansfjnauwnjsanjsnfanwjnasjknsajknf" //This will trigger the Resume
      child ! Report
      expectMsg(3) //nothing changed
    }

    "restart w/ empty sentence" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I Love akka"
      child ! Report
      expectMsg(3)

      child ! ""
      child ! Report
      expectMsg(0) //becaise it restarted
    }

    "should terminate in case of major error" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)

      child ! "ola!" //this will cause exception
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }

    "escalate an error when doesn't know what to do" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)

      child ! 43

      val terminatedMessage =
        expectMsgType[
          Terminated
        ] //child actors also die when escalating the error
      assert(terminatedMessage.actor == child)
    }
  }

  "a kinder supervisor" should {
    "not kill children if failures" in {
      val supervisor =
        system.actorOf(Props[NoDeathOnRestartSupervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "Akka is cool"
      child ! Report
      expectMsg(3)

      child ! 45
      child ! Report
      expectMsg(0) //child is still alive!!!
    }
  }

  "an all for one supervisor" should {
    "apply the all for one strategy" in {
      val supervisor =
        system.actorOf(Props[AllForOneSupervisor], "allforonesup")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      supervisor ! Props[FussyWordCounter]
      val child2 = expectMsgType[ActorRef]

      child2 ! "Tust testing"
      child2 ! Report
      expectMsg(2)

      EventFilter[NullPointerException]() intercept {
        child ! ""
      }

      Thread.sleep(500)

      child2 ! Report
      expectMsg(0)
    }
  }
}

object SupervisionSpec {

  class Supervisor extends Actor {
    override def supervisorStrategy: SupervisorStrategy =
      OneForOneStrategy() {
        case _: NullPointerException     => Restart //extends directive!
        case _: IllegalArgumentException => Stop
        case _: RuntimeException         => Resume
        case _: Exception =>
          Escalate //escalate send a message with the exception to the parent
      }
    def receive: Receive = {
      case props: Props =>
        val child = context.actorOf(props)
        sender() ! child
    }
  }

  class NoDeathOnRestartSupervisor extends Supervisor {
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      //does nothing!!
    }
  }

  class FussyWordCounter extends Actor {
    var words = 0
    override def receive: Receive = {
      case Report => sender() ! words
      case ""     => throw new NullPointerException("Empty sentence")
      case sentence: String =>
        if (sentence.length > 20) throw new RuntimeException("Too biggg")
        else if (!Character.isUpperCase(sentence(0)))
          throw new IllegalArgumentException("Must start w/ uppercase")
        else words += sentence.split(" ").length
      case _ => throw new Exception("can only receive strings")
    }

  }

  object Report

  class AllForOneSupervisor extends Supervisor {
    override def supervisorStrategy: SupervisorStrategy =
      AllForOneStrategy() {
        case _: NullPointerException     => Restart //extends directive!
        case _: IllegalArgumentException => Stop
        case _: RuntimeException         => Resume
        case _: Exception =>
          Escalate //escalate send a message with the exception to the parent
      }

  }

  //while the one for one strategy only applies the strategy to the child that fails.
  //the all for one applies it to all children
}

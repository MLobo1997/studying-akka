package part3testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

class TestProbeSpec
    extends TestKit(ActorSystem("TestProbeSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with BeforeAndAfterAll {
  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TestProbeSpec._

  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to the slave actor" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
      master ! Work("work")
      slave.expectMsg(SlaveWork("work", testActor))
      slave.reply(WorkCompleted(1, testActor))

      expectMsg(Report(1))
    }

    "aggregate data correctly" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workString = "nananan an nsa"
      master ! Work(workString)
      master ! Work(workString)

      slave.receiveWhile() {
        //the punctuation says it must match these exact values
        case SlaveWork(`workString`, `testActor`) =>
          slave.reply(WorkCompleted(3, testActor))

      }

      expectMsg(Report(3))
      expectMsg(Report(6))
    }
  }
}

object TestProbeSpec {
  case class Register(slaveRef: ActorRef)
  case class SlaveWork(text: String, originalRequester: ActorRef)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case class Work(text: String)
  case class Report(wordCount: Int)
  case object RegistrationAck

  class Master extends Actor {
    def receive: Receive = {
      case Register(slaveRef) =>
        context.become(online(slaveRef, 0))
        sender() ! RegistrationAck
      case _ =>
    }

    def online(slaveRef: ActorRef, totalWordCount: Int): Receive = {
      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newWordCount = totalWordCount + count
        originalRequester ! Report(newWordCount)
        context.become(online(slaveRef, newWordCount))
    }
  }
}

package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import part3testing.TimedAssertionsSpec.{WorkResult, WorkerActor}

import scala.concurrent.duration.DurationInt
import scala.util.Random

class TimedAssertionsSpec
    extends TestKit(
      ActorSystem(
        "",
        ConfigFactory.load().getConfig("specialTimedAssertionsConfig")
      )
    )
    with ImplicitSender
    with AnyWordSpecLike
    with BeforeAndAfterAll {
  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A worker actor" should {
    val workerActor = system.actorOf(Props[WorkerActor])

    "reply in a timely manner" in {
      within(500 millis, 1 seconds) {
        workerActor ! "work"
        expectMsg(WorkResult(42))
      }
    }

    "reply with valid work sequence" in {
      within(1 second) {
        workerActor ! "workSequence"

        val results =
          receiveWhile[Int](max = 2 seconds, idle = 500 millis, messages = 10) {
            case WorkResult(result) => result
          }

        assert(results.sum > 5)
      }
    }

    "reply to a test probe in a timely manner" in {
      within(1 seconds) {
        val probe = TestProbe()
        probe.send(workerActor, "work")
        probe.expectMsg(
          WorkResult(42)
        ) //timeout configured in configs. That is the reason why this failed.
      }

    }
  }
}

object TimedAssertionsSpec {
  class WorkerActor extends Actor {
    override def receive: Receive = {
      case "work" =>
        Thread.sleep(1000)
        sender() ! WorkResult(42)
      case "workSequence" =>
        val r = new Random()
        for (_ <- 1 to 10) {
          Thread.sleep(r.nextInt(50))
          sender() ! WorkResult(1)
        }
    }
  }

  case class WorkResult(result: Int)

}

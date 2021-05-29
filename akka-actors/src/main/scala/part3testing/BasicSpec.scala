package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import part3testing.BasicSpec.{BlackHoleActor, LabTestActor, SimpleActor}

import scala.concurrent.duration.DurationInt
import scala.util.Random

class BasicSpec
    extends TestKit(ActorSystem("BasicSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system) //system is member of testkit
  }

  "The thing being tested" should {
    "do this" in {
      //testing scenario
    }
  }

  "A simple actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[SimpleActor])
      val message = "hello, test"
      echoActor ! message

      expectMsg(message)
      testActor //part of the implicit sender. It is the one receiving everything instead of dead letter
    }
  }

  "Blackhole actor" should {
    "not send back some message" in {
      val blackHole = system.actorOf(Props[BlackHoleActor])
      blackHole ! "nana"
      expectNoMessage(1 second)
    }
  }

  "Lab test actor" should {
    val actor = system.actorOf(Props[LabTestActor])

    "turn string to upper case" in {
      actor ! "Na na"
      val reply = expectMsgType[String]
      assert(reply == "NA NA")
    }

    "reply to greeting" in {
      actor ! "greeting"
      expectMsgAnyOf("hi", "hello")
    }

    "reply with favorite tech" in {
      actor ! "favoriteTech"
      expectMsgAllOf("scala", "akka")
    }

    "reply with fav tech" in {
      actor ! "favoriteTech"
      val msgs = receiveN(2)
    }

    "reply with fav tech partial function" in {
      actor ! "favoriteTech"
      expectMsgPF() {
        case "scala" =>
        case "akka"  =>
      }
    }
  }
}

object BasicSpec {
  class SimpleActor extends Actor {
    def receive: Receive = {
      case message => sender() ! message
    }
  }

  class BlackHoleActor extends Actor {
    def receive: Receive = Actor.emptyBehavior
  }

  class LabTestActor extends Actor {
    val random = new Random()
    def receive: Receive = {
      case "greeting" =>
        if (random.nextBoolean()) sender() ! "hi" else sender() ! "hello"
      case "favoriteTech" =>
        sender() ! "scala"
        sender() ! "akka"
      case msg: String => sender() ! msg.toUpperCase
    }
  }
}

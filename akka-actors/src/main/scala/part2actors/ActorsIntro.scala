package part2actors

import akka.actor.{Actor, ActorSystem, Props}

object ActorsIntro extends App {
  val actorSystem =
    ActorSystem("1stActorSystem") //in most cases you only have one of these

  //actors are uniquely identified.
  //messages are asynchrosnous
  //actors may respond differently
  //actors are completely encapsulated

  //defining actor
  class WordCountActor extends Actor {
    var totalWords = 0

    def receive: PartialFunction[Any, Unit] = {
      case message: String =>
        println(s"I received $message")
        totalWords += message.split(" ").length
      case msg => println(s"I cannot understand $msg")
    }

  }
  //this is an actorRef
  val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
  val anotherWordCounter = actorSystem.actorOf(
    Props[WordCountActor],
    "anotherWordCounter"
  ) //must have different name

  //communicate
  wordCounter ! "ahahahahah" //tell
  wordCounter ! 1124
  wordCounter ! "nana"

  class Person(name: String) extends Actor {
    def receive: Receive = {
      case "Hi" => println(s"my name is $name")
      case _    => println("Whaaat")
    }
  }

  object Person {
    def props(name: String): Props = Props(new Person(name))
  }

  //how you pass actor class with args
  val personActor = actorSystem.actorOf(Person.props("Bob"))

  personActor ! "Hi"
}

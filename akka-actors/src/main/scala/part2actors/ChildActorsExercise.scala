package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChildActorsExercise.WordCounterMaster.{
  Initialize,
  WordCountReply,
  WordCountTask
}

object ChildActorsExercise extends App {
  //Distributed word counting
  class WordCounterMaster extends Actor {
    def receive: Receive = withWorkers(List())

    def withWorkers(workers: List[ActorRef]): Receive = {
      case Initialize(nChildren) =>
        val newWorkers =
          for (i <- 1 to nChildren)
            yield context.actorOf(Props[WordCounterWorker], s"worker-$i")
        /*
        val newWorkers = (1 to nChildren).map(i =>
          context.actorOf(Props[WordCounterWorker], s"worker-$i")
        )
         */
        context become withWorkers(workers ++ newWorkers)
      case WordCountReply(count, requester) =>
        requester ! s"Found $count words"
      case text: String =>
        val newWorkersList = workers.headOption.map(worker => {
          worker ! WordCountTask(text, sender())
          workers.tail :+ worker
        })
        context become withWorkers(newWorkersList getOrElse workers)
    }
  }

  object WordCounterMaster {
    case class Initialize(nChildren: Int)
    case class WordCountTask(text: String, requester: ActorRef)
    case class WordCountReply(count: Int, requester: ActorRef)
  }

  class WordCounterWorker extends Actor {
    def receive: Receive = {
      case WordCountTask(text, requester) =>
        println(s"I am ${self.path.name} and I will count '$text'")
        val count = text.split("\\W+").length
        sender() ! WordCountReply(count, requester)
    }
  }

  val system = ActorSystem("WordCounter")

  class TestActor extends Actor {
    def receive: Receive = {
      case "go" =>
        val master = context.actorOf(Props[WordCounterMaster], "master")
        master ! Initialize(3)
        master ! "Ok"
        master ! "Ok my"
        master ! "Ok my friend"
        master ! "Ok my firen how"
        master ! "Ok my firen how are"
        master ! "Ok my firen how are you"
      case msg =>
        println(msg)
    }
  }

  val test = system.actorOf(Props[TestActor], "test")

  test ! "go"

  system.terminate()
}

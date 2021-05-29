package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChangingActorBehavior.FussyKid.{
  HAPPY,
  KidAccept,
  KidReject,
  SAD
}
import part2actors.ChangingActorBehavior.Mom.{
  Ask,
  CHOCOLATE,
  Food,
  MomStart,
  VEGETABLE
}
import part2actors.ChangingActorBehavior.VoteAggregator.{Votes, getNewVoteMap}

object ChangingActorBehavior extends App {

  class FussyKid extends Actor {
    var state: String = HAPPY
    def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        state match {
          case HAPPY => sender() ! KidAccept
          case SAD   => sender() ! KidReject
        }
    }
  }

  object FussyKid {
    case object KidReject
    case object KidAccept
    val HAPPY = "happy"
    val SAD = "sad"
  }

  class StatelessFussyKid extends Actor {
    def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, discardOld = false)
      case Food(CHOCOLATE) =>
      case Ask(_)          => sender() ! KidAccept
    }
    def sadReceive: Receive = {
      case Food(VEGETABLE) =>
      case Food(CHOCOLATE) => context.unbecome()
      case Ask(_)          => sender() ! KidReject
    }
  }

  class Mom extends Actor {
    def receive: Receive = {
      case MomStart(kidRef) =>
        kidRef ! Food(VEGETABLE)
        kidRef ! Ask("Wanna play?")
        kidRef ! Food(CHOCOLATE)
        kidRef ! Ask("Wanna play?")
      case KidAccept => println("HE ACCEPTED :DDDD")
      case KidReject => println("HE REFUSED DDDDD:")
    }
  }

  object Mom {
    case class MomStart(kidRef: ActorRef)
    case class Food(food: String)
    case class Ask(message: String)
    val VEGETABLE = "Veggies"
    val CHOCOLATE = "chocolate"
  }

  val system = ActorSystem("changingActorState")
  val mom = system.actorOf(Props[Mom])
  val kid = system.actorOf(Props[FussyKid])
  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid])

  //mom ! MomStart(kid)
  mom ! MomStart(statelessFussyKid)

  /**
    *  Exercises
    *
    *
    *
    *
    */

  case class Vote(candidate: String)
  case object VoteStatusRequest
  case class VoteStatusReply(candidate: Option[String])

  class Citizen extends Actor {
    def receive: Receive = {
      case Vote(candidate) =>
        context.become(votedFor(candidate), discardOld = false)
      case VoteStatusRequest =>
        sender() ! VoteStatusReply(None)
    }

    def votedFor(candidate: String): Receive = {
      case VoteStatusRequest =>
        sender() ! VoteStatusReply(Some(candidate))
        context.unbecome()
    }
  }

  case class AggregateVotes(citizens: Set[ActorRef])

  class VoteAggregator extends Actor {
    def receive: Receive = {
      case AggregateVotes(citizens) =>
        citizens.foreach(citizen => citizen ! VoteStatusRequest)
        context become pendingAggregation(citizens, Map.empty)
    }

    def pendingAggregation(
        missingCitizens: Set[ActorRef],
        votes: Votes
    ): Receive = {
      case VoteStatusReply(candidate) =>
        val newMissingCitizens = missingCitizens - sender()
        val newVotes =
          candidate.map(cnd => getNewVoteMap(votes, cnd)).getOrElse(votes)
        if (newMissingCitizens.isEmpty) {
          println(newVotes.mkString("\n"))
          context become receive
        } else {
          context become pendingAggregation(newMissingCitizens, newVotes)
        }
    }

  }

  object VoteAggregator {
    type Votes = Map[String, Int]

    private def getNewVoteMap(votes: Votes, candidate: String): Votes = {
      val update = votes
        .get(candidate)
        .map(votes => (candidate, votes + 1))
        .getOrElse((candidate, 1))
      votes + update
    }
  }

  val alice = system.actorOf(Props[Citizen], "alice")
  val bob = system.actorOf(Props[Citizen], "bob")
  val jon = system.actorOf(Props[Citizen], "jon")
  val daniel = system.actorOf(Props[Citizen], "daniel")
  val emanuel = system.actorOf(Props[Citizen], "emanuel")
  val voteAggregator = system.actorOf(Props[VoteAggregator], "voteAggregator")

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  jon ! Vote("Roland")
  daniel ! Vote("Roland")

  voteAggregator ! AggregateVotes(Set(alice, bob, jon, daniel, emanuel))

  Thread.sleep(2000)
  println("--------------")
  alice ! Vote("Jonas")
  bob ! Vote("Jonas")
  jon ! Vote("lala")
  daniel ! Vote("Roland")

  voteAggregator ! AggregateVotes(Set(alice, bob, jon, daniel, emanuel))

  system.terminate()
}

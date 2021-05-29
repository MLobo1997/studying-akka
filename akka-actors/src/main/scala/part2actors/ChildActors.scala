package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChildActors.CreditCard.{AttachToAccount, CheckStatus}
import part2actors.ChildActors.NaiveBankAccount.{
  Deposit,
  InitializeAccount,
  Withdraw
}
import part2actors.ChildActors.Parent.{CreateChild, TellChild}

object ChildActors extends App {
  class Parent extends Actor {
    def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} creating child")
        val childRef = context.actorOf(Props[Child], name)
        context become withChild(childRef)
    }

    def withChild(ref: ActorRef): Receive = {
      case TellChild(message) => ref forward message
    }
  }

  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }

  class Child extends Actor {
    def receive: Receive = {
      case message => println(s"${self.path} I got: $message")
    }
  }

  /**
    * Guardian actors:
    * - System guardian - manages system actor e.g. logging actors
    * - User guardian - creates all actors when doing system.actorOf.. All actors have /user..... path
    * - Root guardian - contains other two
    * -
    */

  val system = ActorSystem("ParentChildDemo")
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! CreateChild("child")
  parent ! TellChild("heeeellllooooo child")

  val childSelection = system.actorSelection("/user/parent/child")
  childSelection ! "I found you"

  // never pass mutable actor state or THIS to child actors.
  // example:

  class NaiveBankAccount extends Actor {
    var amount = 0
    def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard])
        creditCardRef ! AttachToAccount(this) //baaaaad
      case Deposit(funds) =>
        amount += funds
      case Withdraw(funds) =>
        amount -= funds
    }
  }

  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitializeAccount
  }

  class CreditCard extends Actor {

    def attachedTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path} message was processed")
        account.amount -= 1 //f*cked up
    }

    def receive: Receive = {
      case AttachToAccount(account) =>
        context become attachedTo(account)
    }
  }

  object CreditCard {
    case class AttachToAccount(
        bankAccount: NaiveBankAccount
    ) //this is baaad. Should contain ActorRef
    case object CheckStatus
  }

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount], "bankAccountRef")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  system.terminate()
}

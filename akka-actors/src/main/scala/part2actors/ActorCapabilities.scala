package part2actors

import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ActorCapabilities.Bank.Order
import part2actors.ActorCapabilities.BankAccount.{
  BankMovement,
  Deposit,
  Statement,
  Withdraw
}

object ActorCapabilities extends App {

  class SimpleActor extends Actor {
    // actors have information about their context and about themselves
    context.self
    //

    def receive: Receive = {
      case "Hi!" => sender() ! "Hello there!"
      case message: String =>
        println(s"[${self.path}] I have received $message")
      case number: Int => println(s"I have received number: $number")
      case SpecialMessage(contents) =>
        println(s"I have received something specials: $contents")
      case SendMessageToYourself(message) =>
        println(s"sending $message to myself")
        self ! message
      //messages must be serializable and immutable
      case SayHiTo(actorRef: ActorRef) =>
        (actorRef ! "Hi!")(
          self
        ) //Self is an implicit!! Basically just syntactic sugar
      //basically this identifies to the receiver where the message originated from
      case WirelessPhoneMessage(msg, ref) =>
        println(s"[${self.path.name}] Forwarding to ${ref.path.name}")
        ref forward (msg + "s")
    }
  }

  val system = ActorSystem("actorCapabilitesDemo")

  val simpleActor = system.actorOf(Props[SimpleActor])

  simpleActor ! "hello actor" // who is the sender here? the noSender

  //always use case classes and objects
  case class SpecialMessage(contents: String)

  simpleActor ! SpecialMessage("special contente")

  // context.self equivalent  to `this`

  case class SendMessageToYourself(message: String)

  simpleActor ! SendMessageToYourself("hello myself")

  //how do actors reply

  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  case class SayHiTo(ref: ActorRef)

  alice ! SayHiTo(bob)

  alice ! "Hi!" //we send this message directly to alice and she tries to reply
  //this goes to dead letters. Dead letters is a fake actor which receives messages for null actorrefs

  //forwarding messages. It keeps the original sender

  case class WirelessPhoneMessage(content: String, ref: ActorRef)
  alice ! WirelessPhoneMessage("helloooo", bob)

  system.terminate()

  /**
    * exercises
    */

  case object Increment
  case object Decrement
  case object Print

  class CounterActor(initialCounter: Int) extends Actor {
    def receive: Receive = {
      case Increment | Decrement | Print =>
        context become counterState(initialCounter)
    }

    def counterState(counter: Int): Receive = {
      case Increment => context become counterState(counter + 1)
      case Decrement => context become counterState(counter - 1)
      case Print     => println(s"Current counter: $counter")
    }
  }

  object CounterActor {
    def props(initial: Int = 0): Props = Props(new CounterActor(initial))
  }

  val actorSystem = ActorSystem("exercisesSystem")
  val counterActor = actorSystem.actorOf(CounterActor.props(), "counter")

  counterActor ! Increment
  counterActor ! Print
  counterActor ! Increment
  counterActor ! Print
  counterActor ! Decrement
  counterActor ! Print
  counterActor ! Decrement
  counterActor ! Print
  counterActor ! Decrement
  counterActor ! Print
  counterActor ! Increment
  counterActor ! Print

  /**
    * Bank account exercise
    */

  object BankAccount {
    abstract class BankMovement

    case class Deposit(value: Float) extends BankMovement

    case class Withdraw(value: Float) extends BankMovement

    case object Statement extends BankMovement

    def props(balance: Float = 0): Props = Props(new BankAccount(balance))

  }

  class BankAccount(balance: Float) extends Actor {
    def receive: Receive = {
      case _ => context become bankAccountState(balance)
    }

    def bankAccountState(balance: Float): Receive = {
      case Deposit(value) =>
        if (value <= 0) {
          sender() ! Failure(
            new RuntimeException(s"Invalid deposit amount $value")
          )
        } else {
          sender() ! Success
          context become bankAccountState(balance + value)
        }
      case Withdraw(value) =>
        val newBalance = balance - value
        if (newBalance < 0 || value < 0) {
          sender() ! Failure(
            new RuntimeException(
              s"Invalid withdrawal amount. Amount: $value. Balance: $balance"
            )
          )
        } else {
          sender() ! Success
          context become bankAccountState(newBalance)
        }
      case Statement =>
        sender() ! s"Current balance: $balance"
    }
  }

  class Bank extends Actor {
    def receive: Receive = {
      case Order(accountRef) =>
        accountRef ! Deposit(-100)
        accountRef ! Statement
        accountRef ! Withdraw(-100)
        accountRef ! Statement
        accountRef ! Withdraw(100)
        accountRef ! Statement
        accountRef ! Deposit(200)
        accountRef ! Statement
        accountRef ! Withdraw(1200)
        accountRef ! Statement
        accountRef ! Withdraw(500)
        accountRef ! Statement
      case message => println(message)
    }
  }

  object Bank {
    case class Order(accountRef: ActorRef)
  }

  val bank = actorSystem.actorOf(Props[Bank])
  val account = actorSystem.actorOf(BankAccount.props(1000))

  bank ! Order(account)

  Thread.sleep(1000)
  actorSystem.terminate()
}

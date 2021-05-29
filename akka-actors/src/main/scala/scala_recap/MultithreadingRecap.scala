package scala_recap

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MultithreadingRecap extends App {

  val thread = new Thread(() => println("annana"))
  thread.start()
  thread.join()

  val threadHello = new Thread(() => (1 to 1000).foreach(_ => println("hello")))
  val threadBye = new Thread(() => (1 to 1000).foreach(_ => println("bye")))
  threadHello.start()
  threadBye.start()

  case class BankAccount(@volatile private var amount: Int) { //volatile is equivalent to synchronized
    def withdraw(money: Int) = this.amount -= money //not threadsafe

    def safeWithdraw(money: Int) =
      this.synchronized {
        this.amount -= money
      }
  }

  import scala.concurrent.ExecutionContext.Implicits.global
  val futures = Future {
    42
  }

  futures.onComplete {
    case Success(42)        => "Gooood"
    case Failure(exception) => "baaad"
  }

  val aProcessedFuture = futures.map(_ + 1)
  val aFlatFuture = futures.flatMap { value => Future(value + 2) }

  val filteredValue = futures.filter(_ % 2 == 0)
}

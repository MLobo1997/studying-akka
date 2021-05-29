package scala_recap

import scala.concurrent.Future

object AdvancedRecap extends App {
  // partial functions are just syntactic jugar to avoid the match stuff
  val partialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2 => 1352
    case 5 => 21
  }

  //equivalent to
  val pf = (x: Int) =>
    x match {
      case 1 => 42
      case 2 => 1352
      case 5 => 21
    }

  val f: Int => Int = partialFunction

  List(1, 2, 3).map {
    case 1 => 1
    case 2 => 1
    case 3 => 1
  }

  // lifting //when you lift a non exaustive partial function its non covered cases become None
  val lifted = partialFunction.lift // total function Int => Option[Int]
  lifted(2)
  lifted(500)

  partialFunction.orElse[Int, Int] {
    case 200 => 900
  }

  //type aliases
  type ReceiveFunction = PartialFunction[Any, Unit]

  def receive: ReceiveFunction = {
    case 1 => println("nana")
    case _ => println("confused")
  }

  //implicits
  def setTimeout(f: Int => Unit)(implicit timeout: Int) = f(timeout)

  implicit val timeout: Int = 300

  setTimeout(x => println(s"${x}"))

  case class Person(name: String) {
    def greet = s"Hi, my name is $name"
  }

  implicit def fromStringToPerson(string: String): Person = Person(string)
  //you can use implicits as extension functions
  //not extension function. it just tells how to convert a type to another
  val p = "Person"

  // implicit classes
  implicit class Dog(name: String) {
    def bark = 1
  }

  "Lassie".bark //wooooow

  //organizing implicits

  //local scope
  implicit val inverseOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  List(1, 2, 3).sorted //List(3,2,1) dangerous!

  //imported scope
  import scala.concurrent.ExecutionContext.Implicits.global
  val future = Future {
    println("Hello future")
  }

  object Person {
    implicit val personOrdering: Ordering[Person] =
      Ordering.fromLessThan((a, b) => a.name.compareTo(b.name) < 0)
  }

  List(
    Person("Bob"),
    Person("Alice")
  ).sorted //It can sort thanks to the implicit
}

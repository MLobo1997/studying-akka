package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Substreams extends App {
  implicit val system: ActorSystem = ActorSystem("substreams")
  import system.dispatcher

  // 1 - grouping a stream
  val wordsSource = Source(List("a", "list", "of", "string", "ok"))
  val groups = wordsSource.groupBy(
    30,
    str => if (str.isEmpty) '\u0000' else str.toLowerCase().charAt(0)
  )

  groups
    .to(Sink.fold(0)((count, word) => {
      val newCount = count + 1
      println(s"I'v just received $word, count is $newCount")
      newCount
    }))
    .run() // each substream has a different materialization

  // 2 - merge substreams back
  val textSource = Source(
    List(
      "hello my friend",
      "how are you",
      "la la laaa"
    )
  )

  val totalCharCountFuture = textSource
    .groupBy(2, string => string.length % 2) //paralellizing computation
    .map(_.length)
    .mergeSubstreams //WithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  totalCharCountFuture.onComplete {
    case Success(value) =>
      println(value)
    case Failure(exception) =>
      println(exception)
  }

  // 3 - split when a condition is mt
  val text = "asjfnanwf\n" +
    " awfaiwfoikasnf na\n" +
    " asannasj fnajk fn\n" +
    " asfnasn fsafaw waf\n" +
    "jafjasbf"

  val anotherCharCountFuture = Source(text.toList)
    .splitWhen(c => c == '\n')
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  anotherCharCountFuture.onComplete {
    case Success(value) =>
      println(value)
    case Failure(exception) =>
      println(exception)
  }

  val simpleSource = Source(1 to 5)
  simpleSource
    .flatMapConcat(x => Source(x to (3 * x)))
    .runWith(Sink.foreach[Int](x => println(s"Hello: $x")))
}

package part1_primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStreams extends App {
  implicit val system: ActorSystem = ActorSystem("materilizingStreams")

  val simpleGraph =
    Source(1 to 10).to(
      Sink.foreach(println)
    ) //the left most materialized value is the return

  //val simpleMaterializedValue: NotUsed = simpleGraph.run()

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int]((a, b) => a + b)
  val sumFuture: Future[Int] = source.runWith(sink)

  sumFuture.onComplete {
    //this result is the materialized value
    case Success(value) =>
      println(s"The sum of all elements is: $value")
    case Failure(exception) =>
      println(s"The sum could not be computed: $exception")
  }

  //choosing materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleSink = Sink.foreach[Int](println)
  //simpleSource.viaMat(simpleFlow)((sourceMat, flowMat) => flowMat)
  //we have configured the materialized value to be the output of simplesink
  val graph =
    simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  graph.run().onComplete {
    case Success(_) =>
      "Stream processing finished"
    case Failure(ex) =>
      s"Stream failed: $ex"
  }

  //sugars
  Source(1 to 10).runWith(Sink.reduce[Int](_ + _))
  //source.to(Sink.reduce[Int])(Keep.right)
  Source(1 to 10).runReduce(_ + _)

  //u can also do it backwards
  Sink.foreach[Int](println).runWith(Source.single(42))
  // both ways
  Flow[Int].map(2 * _).runWith(simpleSource, simpleSink)

  /**
    * Exercises
    */
  //1
  Thread.sleep(100)
  println("Ex 1:")
  val lastSource = Source(1 to 10)
  val lastSink = Sink.last[Int]
  val futureResult: Future[Int] = lastSource.runWith(lastSink)
  futureResult.foreach(println)

  lastSource.toMat(lastSink)(Keep.right).run().foreach(println)
  lastSource.runReduce((_, b) => b).foreach(println)

  //2
  Thread.sleep(100)
  println("Ex 2:")
  val sentencesSource = Source(
    List("I am a friend", "hello there", "a cool akka project", "shut up")
  )
  val sentenceWordCounterFlow =
    Flow[String].map[Int](sent => sent.split("\\s+").length)
  val sentenceSink = Sink.foreach[Int](println)

  sentencesSource
    .viaMat(sentenceWordCounterFlow)(Keep.right)
    .toMat(sentenceSink)(Keep.right)
    .run()

  sentenceWordCounterFlow.runWith(sentencesSource, sentenceSink)

  system.terminate()
}

package part1_primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {
  implicit val system: ActorSystem = ActorSystem("FirstPrinciples")
  //val materializer = ActorMaterializer() //no longer needed cuz deprecated

  //sources
  val source: Source[Int, NotUsed] = Source(1 to 10)

  //sink
  val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  //processor
  val graph = source.to(sink)
  graph.run()

  // flows transform elements
  val flow = Flow[Int].map(x => x + 1)
  val sourceWithFlow: Source[Int, NotUsed] = source.via(flow)
  //if you combine a flow with a source you get another source (because it publishes values

  //if you combine a flow with a sink, you get another sink
  val flowWithSink: Sink[Int, NotUsed] = flow.to(sink)

  Thread.sleep(100)
  source.to(flowWithSink).run()

  //nuls are not allowed
  //val illegalSource = Source.single[String](null)
  //illegalSource.to(Sink.foreach(println)).run()
  //use options instead

  //various kinds of source
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource =
    Source(LazyList.from(1)) //do not confuse Akka stream with collection stream

  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.future(Future(42))

  //sinks
  val sinkThatDoesNothing = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // Retrieves head and then closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)

  //flows - usually mapped to collection operators
  val mapFlow = Flow[Int].map(x => 2 * x)
  val takFlow = Flow[Int].take(5) //closes the stream after 5 elements

  /*
  streams are
  source -> flow -> flow -> ... -> flow -> sink
   */

  val doubleFlowGraph = source.via(mapFlow).via(takFlow).to(sink)
  Thread.sleep(100)
  doubleFlowGraph.run()

  //syntactic sugar
  //val mapSource = Source(1 to 10).via(Flow[Int].map(x => x * 2))
  val mapSource: Source[Int, NotUsed] = Source(1 to 10).map(x => x * 2)

  //run streams directly
  mapSource.runForeach(println)

  // Operators = components
  /**
    * Ex: create a stream that takes the names of persons, then you will keep the first 2 names with length > 5 characters
    */

  val exSource = Source(
    List(
      "ana",
      "joana",
      "alirio",
      "ze",
      "teresa",
      "josefino",
      "tarantino",
      "mala"
    )
  )

  val filterFlow = Flow[String].filter(name => name.length > 5)
  val takeFlow = Flow[String].take(2)

  val printSink = Sink.foreach(println)

  Thread.sleep(100)
  val exGraph = exSource.via(filterFlow).via(takeFlow).to(printSink).run()
  Thread.sleep(100)
  exSource.filter(name => name.length > 5).take(2).runForeach(println)

  system.terminate()
}

package part2_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{
  Balance,
  Broadcast,
  Flow,
  GraphDSL,
  Merge,
  RunnableGraph,
  Sink,
  Source,
  Zip
}

import scala.concurrent.duration.DurationInt

object GraphBasics extends App {
  implicit val system: ActorSystem = ActorSystem("GraphBasics")

  val input = Source(1 to 10)
  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)
  val output = Sink.foreach[(Int, Int)](println)

  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(Zip[Int, Int]())

      //tie up the components
      input ~> broadcast //input feeds into broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1
      zip.out ~> output

      //return ClosedShape
      ClosedShape //here we freeze the builder's shape -> it become immutable
    }
  )

  graph.run()

  /**
    * ex1: feed source into 2 sinks
    */

  val sink1 = Sink.foreach[Int](x => println(s"Sink 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Sink 2: $x"))

  val graph2 = RunnableGraph.fromGraph(GraphDSL.create() {
    implicit builder: GraphDSL.Builder[NotUsed] =>
      val broadcast = builder.add(Broadcast[Int](2))

      input ~> broadcast ~> sink1 //implicit port numbering
      broadcast ~> sink2
      /*
      broadcast.out(0) ~> sink1
      broadcast.out(1) ~> sink2
       */

      ClosedShape
  })

  Thread.sleep(100)
  graph2.run()

  /**
    * ex2: merge and balance
    */
  val fastSource = Source(1 to 10)
  val slowSource = Source(1 to 10).throttle(3, 1 second)

  val graph3 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      val merge =
        builder.add(
          Merge[Int](2)
        ) //this merger  graph is good for situation where you have several input streams with unknown frequency but you want a steady flow through the output
      val balance = builder.add(Balance[Int](2))

      fastSource ~> merge
      slowSource ~> merge

      merge ~> balance

      balance ~> sink1
      balance ~> sink2

      ClosedShape
    }
  )

  Thread.sleep(1000)
  println("------------Ex 3:")
  graph3.run()

  Thread.sleep(10000)
  system.terminate()
}

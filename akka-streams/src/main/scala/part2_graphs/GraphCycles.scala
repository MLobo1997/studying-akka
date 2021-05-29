package part2_graphs

import akka.actor.ActorSystem
import akka.stream.{
  ClosedShape,
  FlowShape,
  OverflowStrategy,
  UniformFanInShape,
  UniformFanOutShape
}
import akka.stream.scaladsl.{
  Broadcast,
  Flow,
  GraphDSL,
  Merge,
  MergePreferred,
  RunnableGraph,
  Source,
  UnzipWith,
  ZipWith
}
import GraphDSL.Implicits._

object GraphCycles extends App {
  implicit val system: ActorSystem = ActorSystem("Cycles")

  val accelerator = GraphDSL.create() { implicit builder =>
    val sourceShape = builder.add(Source(1 to 100))

    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    incrementerShape ~> mergeShape

    ClosedShape
  } //this way the graph stops
  //this is known as a cycle deadlock. Because of the backpressure signals no component can move on

  /*
  Solution 1: MergePreferred
   */
  val actualAccelerator = GraphDSL.create() { implicit builder =>
    val sourceShape = builder.add(Source(1 to 100))

    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      Thread.sleep(10)
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    incrementerShape ~> mergeShape.preferred

    ClosedShape
  }

  /*
  Sol 3: buffer
   */

  val bufferedAccelerator = GraphDSL.create() { implicit builder =>
    val sourceShape = builder.add(Source(1 to 100))

    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape =
      builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
        println(s"Accelerating $x")
        Thread.sleep(100)
        x
      })

    sourceShape ~> mergeShape ~> incrementerShape
    incrementerShape ~> mergeShape

    ClosedShape
  }

  //cycles risk deadlocks
  val sumZip = ZipWith[BigInt, BigInt, (BigInt, BigInt)]((i1, i2) => {
    val sum = i1 + i2
    Thread.sleep(100)
    println(sum)
    (i2, sum)
  })

  val unzip = UnzipWith[(BigInt, BigInt), BigInt, BigInt](identity)

  val fibonnaciGraph = GraphDSL.create() { implicit builder =>
    val sourceShape = builder.add(Source.single[BigInt](1))
    val sourceBroadcast = builder.add(Broadcast[BigInt](2))
    val inputOneAlt = builder.add(MergePreferred[BigInt](1))
    val inputTwoAlt = builder.add(MergePreferred[BigInt](1))
    val sumZipShape = builder.add(sumZip)
    val unzipShape = builder.add(unzip)

    sourceShape.out ~> sourceBroadcast.in

    sourceBroadcast.out(0) ~> inputOneAlt
    sourceBroadcast.out(1) ~> inputTwoAlt

    inputOneAlt.out ~> sumZipShape.in0
    inputTwoAlt.out ~> sumZipShape.in1

    sumZipShape.out ~> unzipShape.in

    inputOneAlt.preferred <~ unzipShape.out0
    inputTwoAlt.preferred <~ unzipShape.out1

    ClosedShape
  }

  RunnableGraph.fromGraph(fibonnaciGraph).run()
}

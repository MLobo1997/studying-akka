package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{
  Balance,
  GraphDSL,
  Merge,
  RunnableGraph,
  Sink,
  Source
}
import akka.stream.{ClosedShape, Graph, Inlet, Outlet, Shape}
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import scala.language.postfixOps

object CustomGraphShapes extends App {
  implicit val system: ActorSystem = ActorSystem("CustomShapes")

  //balance 2x3 shape
  case class Balance2x3(
      in0: Inlet[Int],
      in1: Inlet[Int],
      out0: Outlet[Int],
      out1: Outlet[Int],
      out2: Outlet[Int]
  ) extends Shape {
    // Inlet[T], Outlet[T]

    val inlets: Seq[Inlet[_]] = List(in0, in1)

    val outlets: Seq[Outlet[_]] = List(out0, out1, out2)

    def deepCopy(): Shape =
      Balance2x3(
        in0.carbonCopy(),
        in1.carbonCopy(),
        out0.carbonCopy(),
        out1.carbonCopy(),
        out2.carbonCopy()
      )
  }

  val balance2x3Impl = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](3))

    merge ~> balance

    Balance2x3(
      merge.in(0),
      merge.in(1),
      balance.out(0),
      balance.out(1),
      balance.out(2)
    )
  }

  val balance2x3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
      val fasterSource = Source(LazyList.from(1)).throttle(2, 1 second)

      def createSink(index: Int) =
        Sink.fold(0)((count: Int, element: Int) => {
          println(s"[sink $index]\t$element, current count: $count")
          count + 1
        })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balance2x3 = builder.add(balance2x3Impl)

      slowSource ~> balance2x3.in0
      fasterSource ~> balance2x3.in1
      balance2x3.out0 ~> sink1
      balance2x3.out1 ~> sink2
      balance2x3.out2 ~> sink3

      ClosedShape
    }
  )

  //balance2x3Graph.run()

  /**
    * Exercise: generalize component, make it MxN and with generics
    */

  case class BalanceMxN[T](
      override val inlets: Seq[Inlet[T]],
      override val outlets: Seq[Outlet[T]]
  ) extends Shape {

    def deepCopy(): Shape =
      BalanceMxN(
        inlets.map(_.carbonCopy()),
        outlets.map(_.carbonCopy())
      )
  }

  object BalanceMxN {
    def apply[T](
        inputCount: Int,
        outputCount: Int
    ): Graph[BalanceMxN[T], NotUsed] =
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._
        val merge = builder.add(Merge[T](inputCount))
        val balance = builder.add(Balance[T](outputCount))

        merge ~> balance

        BalanceMxN(
          merge.inlets,
          balance.outlets
        )
      }
  }

  val balancemxnGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
      val fasterSource = Source(LazyList.from(1)).throttle(2, 1 second)

      def createSink(index: Int) =
        Sink.fold(0)((count: Int, element: Int) => {
          println(s"[sink $index]\t$element, current count: $count")
          count + 1
        })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balancemxn = builder.add(BalanceMxN[Int](2, 3))

      slowSource ~> balancemxn.inlets(0)
      fasterSource ~> balancemxn.inlets(1)
      balancemxn.outlets(0) ~> sink1
      balancemxn.outlets(1) ~> sink2
      balancemxn.outlets(2) ~> sink3

      ClosedShape
    }
  )

  balancemxnGraph.run()
}

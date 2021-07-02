package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{
  Attributes,
  FlowShape,
  Inlet,
  KillSwitches,
  Outlet,
  SinkShape,
  SourceShape
}
import akka.stream.stage.{
  GraphStage,
  GraphStageLogic,
  GraphStageWithMaterializedValue,
  InHandler,
  OutHandler
}
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise}
import scala.language.{existentials, postfixOps}
import scala.util.{Failure, Random, Success}

object CustomOperators extends App {
  implicit val system: ActorSystem = ActorSystem("CustomOperators")

  // 1 - custom source emits random numbers
  class RandomNumberGenerator(max: Int)
      extends GraphStage[ /*step 0 - define shape*/ SourceShape[Int]] {
    // step 1 define ports and members of component
    val outPort: Outlet[Int] = Outlet[Int]("randomGenerator")
    val random = new Random()

    // step 2 construct shape
    def shape: SourceShape[Int] = SourceShape(outPort)

    // step 3 create logic
    def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        //step 4 define mutable state
        setHandler(
          outPort,
          new OutHandler {
            // when there is demand from down stream
            def onPull(): Unit = {
              val nextNumber = random.nextInt(max)
              push(outPort, nextNumber)
            }
          }
        )
      }
  }

  val randomNumberGenerator = Source.fromGraph(new RandomNumberGenerator(100))
  //randomNumberGenerator.runWith(Sink.foreach(println))

  // 2 - a custom sink that prints elements in batches of a given size

  class BatchedPrinter[T](batchSize: Int) extends GraphStage[SinkShape[T]] {
    val inPort: Inlet[T] = Inlet[T]("batchedPrinter")

    def shape: SinkShape[T] = SinkShape(inPort)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        override def preStart(): Unit = {
          pull(inPort)
        }
        val batch = new mutable.Queue[T]
        setHandler(
          inPort,
          new InHandler {
            def onPush(): Unit = {
              val nextElement = grab(inPort)
              batch.enqueue(nextElement)
              if (batch.size >= batchSize) {
                println(
                  s"New batch: ${batch.dequeueAll(_ => true).mkString("[", " , ", "]")}"
                )
              }
              pull(inPort) //sends demand upstream
            }

            override def onUpstreamFinish(): Unit = {
              if (batch.nonEmpty) {
                println(
                  s"New batch: ${batch.dequeueAll(_ => true).mkString("[", " , ", "]")}"
                )
                println("Stream finished!!")
              }
            }
          }
        )
      }
  }

  /**
    * Custom flows
    * Create one that simply filters
    * 2 - ports: input and output
    */

  class Filter[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {
    val inPort: Inlet[T] = Inlet[T]("in")
    val outPort: Outlet[T] = Outlet[T]("out")

    def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      new GraphStageLogic(shape) {
        setHandler(
          inPort,
          new InHandler {
            def onPush(): Unit = {
              val nextElement = grab(inPort)
              if (predicate(nextElement)) {
                push(outPort, nextElement)
              } else {
                pull(inPort)
              }
            }
          }
        )
        setHandler(
          outPort,
          new OutHandler {
            def onPull(): Unit = pull(inPort)
          }
        )
      }

    }
  }

  /**
    * Custom materialized values
    */

  //counts nr of elements

  class CounterFlow[T]
      extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {
    val inPort: Inlet[T] = Inlet[T]("in")
    val outPort: Outlet[T] = Outlet[T]("out")

    def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    def createLogicAndMaterializedValue(
        inheritedAttributes: Attributes
    ): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]
      val logic: GraphStageLogic = new GraphStageLogic(shape) {
        var counter = 0
        setHandler(
          inPort,
          new InHandler {
            def onPush(): Unit = {
              push(outPort, grab(inPort))
              counter += 1
            }

            override def onUpstreamFinish(): Unit = {
              promise.success(counter)
              super.onUpstreamFinish()
            }

            override def onUpstreamFailure(ex: Throwable): Unit = {
              promise.failure(ex)
              super.onUpstreamFailure(ex)
            }
          }
        )

        setHandler(
          outPort,
          new OutHandler {
            def onPull(): Unit = pull(inPort)

            override def onDownstreamFinish(cause: Throwable): Unit = {
              promise.success(counter)
              super.onDownstreamFinish(cause)
            }
          }
        )
      }

      (logic, promise.future)
    }
  }

  val batchedSink = Sink.fromGraph(new BatchedPrinter[Int](5))
  val filterCustomFlow = Flow.fromGraph(new Filter[Int](_ > 50))
  val counterFlow = Flow.fromGraph(new CounterFlow[Int]())
  val killSwitch = KillSwitches.single[Int]
  val ((switchMat, counter1Future), counter2Future) =
    randomNumberGenerator
      .viaMat(killSwitch)(Keep.right)
      .viaMat(counterFlow)(Keep.both)
      .viaMat(filterCustomFlow)(Keep.left)
      .viaMat(counterFlow)(Keep.both)
      .map(x => if (x == 82) throw new RuntimeException("Boom!") else x)
      .to(batchedSink)
      .run()

  import system.dispatcher
  system.scheduler.scheduleOnce(3 seconds) {
    switchMat.shutdown()
  }

  counter1Future.onComplete {
    case Failure(exception) => print(exception.toString)
    case Success(value)     => println(value)
  }

  counter1Future.flatMap(x =>
    counter2Future.map(y => {
      println(s"Counter 1: $x, Counter 2: $y")
    })
  )

  for {
    x <- counter1Future
    y <- counter2Future
  } yield {
    println(s"Comprehensive!!! Counter 1: $x, Counter 2: $y")
  }
}

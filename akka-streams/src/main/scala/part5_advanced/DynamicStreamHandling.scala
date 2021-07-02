package part5_advanced

import akka.actor.ActorSystem
import akka.stream.KillSwitches
import akka.stream.scaladsl.{Keep, MergeHub, Sink, Source, BroadcastHub}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object DynamicStreamHandling extends App {
  implicit val system: ActorSystem = ActorSystem("DynamicStreamHandling")
  import system.dispatcher

  // 1: kill switch
  val killSwitchFlow = KillSwitches.single[Int]
  val counter = Source(1 to 10).throttle(1, 1 second).log("counter")
  val sink = Sink.ignore

  /*
  val killSwitch = counter
    .viaMat(killSwitchFlow)(Keep.right)
    .to(sink)
    .run()

  system.scheduler.scheduleOnce(3 seconds) {
    killSwitch.shutdown()
  }
   */

  val anotherCounter =
    Source(LazyList.from(1)).throttle(2, 1 second).log("anotherCounter")
  val sharedKillSwitch = KillSwitches.shared("oneButton")

  counter.via(sharedKillSwitch.flow) //.runWith(Sink.ignore)
  anotherCounter.via(sharedKillSwitch.flow) //.runWith(Sink.ignore)

  system.scheduler.scheduleOnce(3 seconds) {
    sharedKillSwitch.shutdown()
  }

  //MergeHub
  val dynamicMerge = MergeHub.source[Int]

  /*
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run()

  // now do fan-in of other flows to the same sink
  Source(1 to 10).runWith(materializedSink)
  counter.runWith(materializedSink)
   */

  // BroadcastHub

  val dynamicBroadcast = BroadcastHub.sink[Int]
  /*
  val materializedSource = Source(1 to 10).runWith(dynamicBroadcast)

  materializedSource.runWith(Sink.ignore)
  materializedSource.runWith(Sink.foreach[Int](println))
   */

  //pub sub system

  val bcast = BroadcastHub.sink[Int]
  val merge = MergeHub.source[Int]

  val (publisher, subscriber) = merge.toMat(bcast)(Keep.both).run()
  Source(1 to 10).runWith(publisher)
  subscriber.runWith(Sink.foreach[Int](println))
  subscriber.runWith(Sink.foreach[Int](x => println(s"hi there $x")))

}

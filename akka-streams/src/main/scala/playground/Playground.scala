package playground

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}

object Playground extends App {
  // reactive streams are async and backpressured streasm
  /*
  must have:
    publisher
    subscriber
    processor - transformer

   publisher -> Source
   subscriber -> Sink
   processor -> Flow

   upstream  <<<<<< towards the source
   downstream  <<<<<< towards the sink
   */

  implicit val actorSystem: ActorSystem = ActorSystem("Playground")

  Source.single("Hello, Streams!").to(Sink.foreach(println)).run()

  actorSystem.terminate()

}

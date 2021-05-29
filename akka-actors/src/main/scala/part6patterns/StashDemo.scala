package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo extends App {
  /*
  Resource Actor
    - open => it can receive read/write requests to the resource
    - otherwise wait until open
  ResourceActor is closed
    - open => switch to the open state
    - Read Write messages are POSTPONED

  ResourceActor is open
    -Read, Write are handled
    -Close => switch to the closed state

  [OPEN, READ, READ, WRITE]
  -opens
  -reads
  -reads
  -writes

  [READ, OPEN, WRITE]
  -stash read
  -opens
  -reads
  -write
   */
  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  class ResourceActor extends Actor with ActorLogging with Stash {
    private var innerData: String = ""
    def receive: Receive = closed

    def closed: Receive = {
      case Open =>
        log.info("Opening resource")
        unstashAll()
        context.become(open)
      case msg =>
        log.info(s"Stashing message ${msg.toString} cuz it's closed!")
        stash()
    }

    def open: Receive = {
      case Read =>
        //do some actual computation
        log.info(s"Read: $innerData")
      case Write(data) =>
        log.info(s"I am writing $data")
        innerData += data
      case Close =>
        log.info("Closing resource")
        unstashAll()
        context.become(closed)
      case msg =>
        log.info(
          s"Stashing ${msg.toString} because I can't handle in the open state"
        )
        stash()
    }
  }

  val system = ActorSystem("StashDemo")
  val resourceActor = system.actorOf(Props[ResourceActor])

  resourceActor ! Read
  resourceActor ! Open
  resourceActor ! Open
  resourceActor ! Write("i love stash")
  resourceActor ! Close
  resourceActor ! Read

}

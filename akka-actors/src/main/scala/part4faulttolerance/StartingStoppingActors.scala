package part4faulttolerance

import akka.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSystem,
  Kill,
  PoisonPill,
  Props,
  Terminated
}
import part4faulttolerance.StartingStoppingActors.Parent.{
  StartChild,
  Stop,
  StopChild
}

object StartingStoppingActors extends App {

  val system = ActorSystem("StoppingActorDemo")

  class Parent extends Actor with ActorLogging {
    def receive: Receive = withChildren(Map())

    def withChildren(children: Map[String, ActorRef]): Receive = {
      case StartChild(name) =>
        log.info(s"Creating child $name")
        val child = context.actorOf(Props[Child], name)
        context.become(withChildren(children + (name -> child)))
      case StopChild(name) =>
        val childOption: Option[ActorRef] = children.get(name)
        childOption.foreach(child => {
          log.info(s"Killing child $name")
          context.stop(child) //this is asynchronous
          context.become(withChildren(children - name))
        })
      case Stop =>
        log.info("Stopping myself")
        context.stop(self) //also async but stops all child actors
      //stops children first and then parents
    }
  }

  object Parent {
    case class StartChild(name: String)
    case class StopChild(name: String)
    case object Stop
  }

  class Child extends Actor with ActorLogging {
    def receive: Receive = {
      case message => log.info(s"Received: ${message.toString}")
    }
  }

  // method 1 - context.stop
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! StartChild("Child1")
  val child = system.actorSelection("/user/parent/Child1")
  child ! "Hi kid"
  parent ! StopChild("Child1")
  //for (_ <- 1 to 50) child ! "Are you alive????"
  parent ! StartChild("Child2")
  val child2 = system.actorSelection("/user/parent/Child2")
  child2 ! "Hi 2nd child"

  parent ! Stop

  for (_ <- 1 to 10) parent ! "pareeeeeent"
  for (_ <- 1 to 100) child2 ! "chihhhhiiiiiiild"

  // Method 2 - special messages

  val looseActor = system.actorOf(Props[Child])
  looseActor ! "hello, loose actor"
  looseActor ! PoisonPill
  looseActor ! "Loose actor are you still there???/"

  val abruptlyTerminatedActor = system.actorOf(Props[Child])
  abruptlyTerminatedActor ! "you are terminated"
  abruptlyTerminatedActor ! Kill //kills through an exception
  abruptlyTerminatedActor ! "DEAD????"

  //death watch
  class Watcher extends Actor with ActorLogging {
    def receive: Receive = {
      case StartChild(name) =>
        val child = context.actorOf(Props[Child], name)
        log.info(s"Started and watching child $name")
        context.watch(child) //when the child dies, this receives a terminated
      case Terminated(ref) =>
        log.info(
          s"The reference that I'm watching ${ref.path.name} has been stopped"
        )
    }
  }

  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("watchedChild")
  val watchedChild = system.actorSelection("/user/watcher/watchedChild")
  watchedChild ! "You are there"
  Thread.sleep(500)
  watchedChild ! PoisonPill

  system.terminate()
}

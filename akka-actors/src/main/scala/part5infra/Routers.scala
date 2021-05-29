package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing.{
  ActorRefRoutee,
  Broadcast,
  FromConfig,
  RoundRobinGroup,
  RoundRobinPool,
  RoundRobinRoutingLogic,
  Router
}
import com.typesafe.config.ConfigFactory

object Routers extends App {

  /**
    * Manual router
    */
  class Master extends Actor {
    // step 1 - create routees
    private val slaves = for (i <- 1 to 5) yield {
      val slave = context.actorOf(Props[Slave], s"slave$i")
      context.watch(slave)
      ActorRefRoutee(slave)
    }
    //define the router
    private var router = Router(RoundRobinRoutingLogic(), slaves)

    def receive: Receive = {
      // route the messages
      case message =>
        router.route(message, sender()) //this makes a forward
      //handle the termination of routees
      case Terminated(ref) =>
        router = router.removeRoutee(ref)
        val newSlave = context.actorOf(Props[Slave])
        router = router.addRoutee(newSlave)
    }
  }

  class Slave extends Actor with ActorLogging {
    def receive: Receive = {
      case message =>
        log.info(message.toString)
    }
  }

  val system =
    ActorSystem("RoutersDemo", ConfigFactory.load().getConfig("routersDemo"))
  val master = system.actorOf(Props[Master])
  for (i <- 1 to 10)
    master ! s"Hello there $i"

  /** Routing strattegies
    *   round robin
    *    random = not so good
    *    smallest mailbox
    *    broadcast = redundency manager
    *    scatter gather first = broadcasts and waits for the first reply (all others are discarded
    *    tail chopping = send to a routee, if he doesnt reply in time send to another
    *    consistent hashing = all messages from same hash reach same routee.
    */

  /**
    * Method #2 - a router actor with is own chilren
    */

  val poolMaster =
    system.actorOf(RoundRobinPool(5).props(Props[Slave]), "simplePoolMaster")
  for (i <- 1 to 10)
    poolMaster ! s"Hello pool master ${i}"

  //from config
  val poolMaster2 =
    system.actorOf(FromConfig.props(Props[Slave]), "poolMaster2")
  for (i <- 1 to 10)
    poolMaster2 ! s"Hello pool configured master ${i}"

  /**
    * Router w/ actors created elsewhere
    * Group router
    */
  val slaveList =
    (1 to 5).map(i => system.actorOf(Props[Slave], s"slave_$i")).toList

  val slavePaths = slaveList.map(slaveRef => slaveRef.path.toString)

  val groupMaster = system.actorOf(RoundRobinGroup(slavePaths).props())
  for (i <- 1 to 10)
    groupMaster ! s"Hello group master 1 ${i}"

  //do it from config
  val groupMaster2 = system.actorOf(FromConfig.props(), "groupMaster2")
  for (i <- 1 to 10)
    groupMaster2 ! s"Hello group master 2 ${i}"

  /**
    * Special messages
    */
  groupMaster2 ! Broadcast("hello, everyone")

  //PoisonPill and Kill are not routed
  //AddRoutee, Remove, Get are handled only by the routing actor

  Thread.sleep(1000)
  system.terminate()
}

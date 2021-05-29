package part2actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object AkkaConfig extends App {
  class SimpleActor extends Actor with ActorLogging {
    def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  /**
    * Inline config
    */

  val s = """
    | akka {
    |   loglevel = "DEBUG"
    | }
    |""".stripMargin

  val config = ConfigFactory.parseString(s)

  val system = ActorSystem("ConfigDemo", ConfigFactory.load(config))
  val actor = system.actorOf(Props[SimpleActor])

  actor ! "A message to remember"
  system.terminate()

  val defaultConfigSystem =
    ActorSystem("DefaultConfig") //it finds the file in resources auto
  val defaultConfigActor =
    defaultConfigSystem.actorOf(Props[SimpleActor], "goood_actor")

  defaultConfigActor ! "default actor"

  defaultConfigSystem.terminate()

  /**
    * Separate config same file
    */

  val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
  val specialConfigSystem = ActorSystem("SpecialConfigDemo", specialConfig)
  val specialConfigActor = specialConfigSystem.actorOf(Props[SimpleActor])

  specialConfigActor ! "special actor"

  specialConfigSystem.terminate()

  /**
    * separate configs
    *
    *
    */

  val separateConfig =
    ConfigFactory.load("secretFolder/secretConfiguration.conf")
  println(s"${separateConfig.getString("akka.load")}")

  //You can also use json!!!! And properties files
  //but in properties files you can't do neste configs :(
}

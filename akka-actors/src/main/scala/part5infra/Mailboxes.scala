package part5infra

import akka.actor.ActorSystem.Settings
import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{
  ControlMessage,
  PriorityGenerator,
  UnboundedPriorityMailbox
}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App {
  val system =
    ActorSystem("mailboxDemo", ConfigFactory.load().getConfig("mailboxesDemo"))

  class SimpleActor extends Actor with ActorLogging {
    def receive: Receive = {
      case msg =>
        log.info(msg.toString)
    }
  }

  /**
    * Case #1 - custom priority mailbox --mailbox which is not FIFO
    * p0 -> most important
    * p1
    * p2
    * p3
    */

  //1 -mailbox definition
  class SupportTicketPriorityMailbox(settings: Settings, config: Config)
      extends UnboundedPriorityMailbox(PriorityGenerator {
        case message: String if message.startsWith("[P0]") => 0
        case message: String if message.startsWith("[P1]") => 1
        case message: String if message.startsWith("[P2]") => 2
        case message: String if message.startsWith("[P3]") => 3
        case _                                             => 4
      })

  //2 - configure it
  //support-ticket-dispather

  //3 attach dispatcher to an actor
  val supportTicketLogger = system.actorOf(
    Props[SimpleActor].withDispatcher("support-ticket-dispatcher")
  )
  supportTicketLogger ! PoisonPill //also gets postponed
  supportTicketLogger ! "[P3] not urgent"
  supportTicketLogger ! "[P0] nowww"
  supportTicketLogger ! "[P1] mid"
  //after how long can I get a message so that they can get prioritized?
  //It is impossible to be configured.

  /**
    * Case #2 - control-aware mailbox
    */

  // step 1 - mark important messages as control messages
  case object ManagementTicket extends ControlMessage
  //step 2 -configure who gets the mailbox
  val controlAwareActor =
    system.actorOf(
      Props[SimpleActor].withMailbox("control-mailbox"),
      "controlAware"
    )
  controlAwareActor ! "[P0] nowww"
  controlAwareActor ! "[P1] mid"
  controlAwareActor ! ManagementTicket

  //method 2 - using deployment config
  val altControlAwareActor =
    system.actorOf(Props[SimpleActor], "alternativeControlAwareActor")

  altControlAwareActor ! "[P0] nowww"
  altControlAwareActor ! "[P1] mid"
  altControlAwareActor ! ManagementTicket

}

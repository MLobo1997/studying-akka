package part3testing

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

class InterceptingLogsSpec
    extends TestKit(
      ActorSystem(
        "InterceptinGlogspecs",
        ConfigFactory.load().getConfig("interceptingLogMessages")
      )
    )
    with ImplicitSender
    with AnyWordSpecLike
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import InterceptingLogsSpec._

  val item = "The iteeem"
  val creditCard = "12481947187489127"

  "A checkout flow" should {
    "correctly log the dispatch of an order" in {
      EventFilter.info(
        pattern = s"Order [0-9]+ for item $item has been dispatched",
        occurrences = 1
      ) intercept {
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout(item, creditCard)
      }
    }

    "freak out if the payment is denied" in {
      EventFilter[RuntimeException](occurrences = 1) intercept {
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout(item, "000") //invalid card
      }
    }
  }
}

object InterceptingLogsSpec {
  class CheckoutActor extends Actor {
    private val paymentManager = context.actorOf(Props[PaymentManager])
    private val fulfillmentManager = context.actorOf(Props[FulfillmentManager])

    def receive: Receive = awaitingCheckout

    def awaitingCheckout: Receive = {
      case Checkout(item, card) =>
        paymentManager ! AuthorizeCard(card)
        context.become(pendingPayment(item))
    }

    def pendingFulfillment(item: String): Receive = {
      case OrderFulfilled =>
        context.become(awaitingCheckout)
    }

    def pendingPayment(item: String): Receive = {
      case PaymentAccepted =>
        fulfillmentManager ! DispatchOrder(item)
        context.become(pendingFulfillment(item))
      case PaymentDenied =>
        throw new RuntimeException("I can't handle this")
    }

  }
  class PaymentManager extends Actor {
    def receive: Receive = {
      case AuthorizeCard(card) =>
        if (card.startsWith("0"))
          sender() ! PaymentDenied
        else
          sender() ! PaymentAccepted
    }
  }

  class FulfillmentManager extends Actor with ActorLogging {
    var orderID = 43
    def receive: Receive = {
      case DispatchOrder(item) =>
        orderID += 43
        log.info(s"Order $orderID for item $item has been dispatched.")
        sender() ! OrderFulfilled
    }
  }

  case class Checkout(item: String, creditCard: String)

  case class AuthorizeCard(card: String)

  case object PaymentAccepted
  case object PaymentDenied

  case class DispatchOrder(item: String)

  case object OrderFulfilled

}

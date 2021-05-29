package part6patterns

import akka.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSystem,
  FSM,
  Props,
  Timers
}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import part6patterns.FsmSpec._

import scala.concurrent.duration.{DurationDouble, DurationInt}
import scala.util.{Failure, Success, Try}

class FsmSpec
    extends TestKit(ActorSystem("FsmSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def initVending(): ActorRef =
    system.actorOf(Props[VendingMachine])

  val inventory = Map(
    "coke" -> 3,
    "sprite" -> 5,
    "chupa-chups" -> 2,
    "lime" -> 1,
    "twix" -> 0
  )

  val prices = Map(
    "coke" -> 1,
    "sprite" -> 2,
    "chupa-chups" -> 1,
    "lime" -> 4,
    "twix" -> 3
  )

  def initOperationalVending(): ActorRef = {
    val vending = initVending()
    vending ! Initialize(inventory, prices)
    vending
  }

  "A vending machine" should {
    "error when not initialized" in {
      val vendingMachine = initVending()
      vendingMachine ! RequestProduct("coke")
      expectMsgType[VendingError]
    }

    "report a product not available" in {
      val vendingMachine = initOperationalVending()
      vendingMachine ! RequestProduct("twix")
      expectMsgType[VendingError]

      vendingMachine ! RequestProduct("na")
      expectMsgType[VendingError]
    }

    "throw timeout" in {
      val vendingMachine = initOperationalVending()
      vendingMachine ! RequestProduct("coke")
      expectMsgType[Instruction]

      within(1.5 seconds) {
        expectMsgType[VendingError]
      }
    }

    def askForMoreMoney(): ActorRef = {
      val vendingMachine = initOperationalVending()
      vendingMachine ! RequestProduct("sprite")
      expectMsgType[Instruction]

      vendingMachine ! ReceiveMoney(1)
      expectMsg(VendingError("You are missing 1€"))
      vendingMachine
    }

    "ask for more money" in {
      askForMoreMoney()

      within(1.5 seconds) {
        expectMsgType[VendingError]
      }
    }

    "ask for more money and accept" in {
      val vendingMachine = askForMoreMoney()
      vendingMachine ! ReceiveMoney(1)
      expectMsg(Deliver("sprite"))
    }

    "sell two products" in {
      val vendingMachine = initOperationalVending()

      vendingMachine ! RequestProduct("coke")
      expectMsgType[Instruction]

      vendingMachine ! ReceiveMoney(1)
      expectMsg(Deliver("coke"))

      vendingMachine ! RequestProduct("sprite")
      expectMsgType[Instruction]

      vendingMachine ! ReceiveMoney(2)
      expectMsg(Deliver("sprite"))
    }
  }
}

object FsmSpec {
  /*
  Vending machine
   */
  case class Initialize(inventory: Map[String, Int], prices: Map[String, Int])
  case class RequestProduct(product: String)

  case class Instruction(instruction: String)
  case class ReceiveMoney(amount: Int)
  case class Deliver(product: String)
  case class GiveBackChange(amount: Int)

  case class VendingError(reason: String)

  case object ReceiveMoneyTimeout

  case object TimerKey

  class VendingMachine extends Actor with ActorLogging with Timers {
    override def receive: Receive = idle

    def waitingForMoney(
        inventory: Map[String, Int],
        prices: Map[String, Int],
        product: String,
        money: Int,
        client: ActorRef
    ): Receive = {
      case ReceiveMoney(receivedMoney) =>
        val totalMoney = money + receivedMoney
        val updatedInventoryAttempt =
          attemptDelivery(inventory, prices, product, totalMoney)
        updatedInventoryAttempt match {
          case Failure(exception) =>
            sender() ! VendingError(exception.getMessage)
            context.become(
              waitingForMoney(inventory, prices, product, totalMoney, client)
            )
          case Success(updatedInventory) =>
            val change = totalMoney - prices(product)
            if (change > 0)
              sender() ! GiveBackChange(change)
            sender() ! Deliver(product)
            context.become(operational(updatedInventory, prices))
        }
      case ReceiveMoneyTimeout =>
        client ! VendingError("Timeoooout")
        if (money > 0)
          client ! GiveBackChange(money)
        context.become(operational(inventory, prices))
    }

    def operational(
        inventory: Map[String, Int],
        prices: Map[String, Int]
    ): Receive = {
      case RequestProduct(product) =>
        inventory.get(product) match {
          case None | Some(0) =>
            sender() ! VendingError("This product is not in the inventory")
          case Some(_) =>
            val price = prices(product)
            sender() ! Instruction(s"Insert ${price}€!")
            timers.startTimerWithFixedDelay(
              TimerKey,
              ReceiveMoneyTimeout,
              1 seconds
            )
            context.become(
              waitingForMoney(inventory, prices, product, 0, sender())
            )
        }
      case ReceiveMoneyTimeout =>
    }

    def idle: Receive = {
      case Initialize(inventory, prices) =>
        context.become(operational(inventory, prices))
      case _ => sender() ! VendingError("Machine is idle!")
    }

    private def attemptDelivery(
        inventory: Map[String, Int],
        prices: Map[String, Int],
        product: String,
        moneyAmount: Int
    ): Try[Map[String, Int]] = {
      val quantity = inventory(product)
      val newInventoryAttempt =
        if (quantity > 0) {
          Success(inventory + (product -> (quantity - 1)))
        } else Failure(new RuntimeException("Not enough"))
      val change = moneyAmount - prices(product)
      newInventoryAttempt.flatMap(it => {
        if (change >= 0)
          Success(it)
        else
          Failure(new RuntimeException(s"You are missing ${change.abs}€"))
      })
    }

  }

  //step 1 - define states and data
  trait VendingState
  case object Idle extends VendingState
  case object Operational extends VendingState
  case object WaitingForMoney extends VendingState

  trait VendingData
  case object Uninitialized extends VendingData
  case class Initialized(inventory: Map[String, Int], prices: Map[String, Int])
      extends VendingData
  case class WaitForMoneyData(
      inventory: Map[String, Int],
      prices: Map[String, Int],
      product: String,
      money: Int,
      requester: ActorRef
  ) extends VendingData

  class VendingMachineFSM extends FSM[VendingState, VendingData] with Timers {
    startWith(Idle, Uninitialized)

    when(Idle) {
      case Event(Initialize(inventory, prices), Uninitialized) =>
        goto(Operational) using Initialized(inventory, prices)
      //equivalent to context.become(operational(...
      case _ =>
        sender() ! VendingError("Machine not initialized")
        stay()
    }

    when(Operational) {
      case Event(RequestProduct(product), Initialized(inventory, prices)) =>
        inventory.get(product) match {
          case None | Some(0) =>
            sender() ! VendingError("This product is not in the inventory")
            stay()
          case Some(_) =>
            val price = prices(product)
            sender() ! Instruction(s"Insert ${price}€!")
            timers.startTimerWithFixedDelay(
              TimerKey,
              ReceiveMoneyTimeout,
              1 seconds
            )
            goto(WaitingForMoney) using WaitForMoneyData(
              inventory,
              prices,
              product,
              0,
              requester = sender()
            )
        }
      case Event(ReceiveMoneyTimeout, _) =>
        stay()
    }

    when(WaitingForMoney) {
      case Event(
            ReceiveMoney(receivedMoney),
            WaitForMoneyData(inventory, prices, product, money, client)
          ) =>
        val totalMoney = money + receivedMoney
        val updatedInventoryAttempt =
          attemptDelivery(inventory, prices, product, totalMoney)
        updatedInventoryAttempt match {
          case Failure(exception) =>
            sender() ! VendingError(exception.getMessage)
            goto(WaitingForMoney) using WaitForMoneyData(
              inventory,
              prices,
              product,
              totalMoney,
              client
            )
          case Success(updatedInventory) =>
            val change = totalMoney - prices(product)
            if (change > 0)
              sender() ! GiveBackChange(change)
            sender() ! Deliver(product)
            goto(Operational) using Initialized(updatedInventory, prices)
        }
      case Event(
            ReceiveMoneyTimeout,
            WaitForMoneyData(inventory, prices, _, money, client)
          ) =>
        client ! VendingError("Timeoooout")
        if (money > 0)
          client ! GiveBackChange(money)
        goto(Operational) using Initialized(inventory, prices)
    }

    whenUnhandled {
      case Event(_, _) =>
        sender() ! VendingError("Not found that command")
        stay()
    }

    onTransition {
      case stateA -> stateB =>
        log.info(s"Transition from $stateA to $stateB")
    }

    initialize()

    private def attemptDelivery(
        inventory: Map[String, Int],
        prices: Map[String, Int],
        product: String,
        moneyAmount: Int
    ): Try[Map[String, Int]] = {
      val quantity = inventory(product)
      val newInventoryAttempt =
        if (quantity > 0) {
          Success(inventory + (product -> (quantity - 1)))
        } else Failure(new RuntimeException("Not enough"))
      val change = moneyAmount - prices(product)
      newInventoryAttempt.flatMap(it => {
        if (change >= 0)
          Success(it)
        else
          Failure(new RuntimeException(s"You are missing ${change.abs}€"))
      })
    }

  }
}

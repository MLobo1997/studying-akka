package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.BeforeAndAfterAll
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.wordspec.AnyWordSpecLike
import part6patterns.AskSpec.AuthManager.{
  AUTH_FAILURE_NOT_FOUND,
  AUTH_FAILURE_PASSWORD_INCORRECT,
  AUTH_FAILURE_SYSTEM
}
import part6patterns.AskSpec._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class AskSpec
    extends TestKit(ActorSystem("AskSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with BeforeAndAfterAll {
  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "An authenticator" should {
    authenticatorTestSuite(Props[AuthManager])
  }

  "A piped authenticator" should {
    authenticatorTestSuite(Props[PipedAuthManager])
  }

  def authenticatorTestSuite(props: Props): Unit = {
    "fail to authenticate non-registered user" in {
      val authManager = system.actorOf(props)
      authManager ! Authenticate("daniel", "rtjvm")
      expectMsg(AuthFailure(AUTH_FAILURE_NOT_FOUND))
    }

    "fail to authenticate if invalid password" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("john", "1234")
      authManager ! Authenticate("john", "wrong")
      expectMsg(AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT))
    }

    "succeed to authenticate" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("john", "1234")
      authManager ! Authenticate("john", "1234")
      expectMsg(AuthSuccess)
    }
  }
}

object AskSpec {
  case class Read(key: String)
  case class Write(key: String, value: String)
  //this code is somewhere else
  class KVActor extends Actor with ActorLogging {
    def receive: Receive = online(Map())

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"Trying to read the value at the $key")
        sender() ! kv.get(key)
      case Write(key, value) =>
        log.info(s"Writing the value $value for the key $key")
        context.become(online(kv + (key -> value)))
    }
  }

  case class RegisterUser(username: String, password: String)
  case class Authenticate(username: String, password: String)
  case class AuthFailure(message: String)
  case object AuthSuccess

  class AuthManager extends Actor with ActorLogging {
    protected val authDB = context.actorOf(Props[KVActor])
    implicit val timeout: Timeout = Timeout(1 second)
    implicit val executionContext: ExecutionContext = context.dispatcher

    override def receive: Receive = {
      case RegisterUser(username, password) =>
        authDB ! Write(username, password)
      case Authenticate(username, password) =>
        handleAuthentication(username, password)
    }

    def handleAuthentication(username: String, password: String): Unit = {
      val originalSender = sender()
      val future = authDB ? Read(username)
      future.onComplete {
        case Success(None) =>
          originalSender ! AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Success(Some(dbPassword)) =>
          //NEVER CALL METHODS OF THE ACTOR INSTANCE OR ACCESS MUTABLE STATE IN ON COMPLETE
          //if (dbPassword == password) sender() ! AuthSuccess //big problem!!!
          if (dbPassword == password) originalSender ! AuthSuccess
          else originalSender ! AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
        case Failure(_) =>
          originalSender ! AuthFailure(AUTH_FAILURE_SYSTEM)
      }
    }
  }

  object AuthManager {
    val AUTH_FAILURE_NOT_FOUND = "username not found"
    val AUTH_FAILURE_PASSWORD_INCORRECT = "password incorrect"
    val AUTH_FAILURE_SYSTEM = "system error"
  }

  class PipedAuthManager extends AuthManager {
    override def handleAuthentication(
        username: String,
        password: String
    ): Unit = {
      val future = authDB ? Read(username)
      val passwordFuture =
        future.mapTo[Option[String]]
      val responseFuture = passwordFuture.map {
        case None =>
          AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Some(dbpassword) =>
          if (dbpassword == password) AuthSuccess
          else AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
      }
      responseFuture.pipeTo(
        sender()
      ) //when the future is completed the responses is piped to the sender
    }
  }

}

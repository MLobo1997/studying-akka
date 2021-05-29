package part4faulttolerance

import akka.actor.SupervisorStrategy.{Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{BackoffOpts, BackoffSupervisor}

import java.io.File
import scala.concurrent.duration.DurationInt
import scala.io.Source

object BackoffSupervisorPattern extends App {

  case object ReadFile
  class FileBasedPersistentActor extends Actor with ActorLogging {
    var dataSource: Source = null

    override def preStart(): Unit = {
      log.info("Persistent actor starting")
    }

    override def postStop(): Unit = {
      log.warning("Persistent actor has stopped")
    }

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.warning("Actor restarting!")
    }

    def receive: Receive = {
      case ReadFile =>
        if (dataSource == null)
          dataSource = Source.fromFile(
            new File(
              "src/main/resources/testFiles/important_data.txt"
            ) //this causes FileNotFound
            //new File("src/main/resources/testFiles/important.txt")
          )
        log.info(s"I'v just read: ${dataSource.getLines().toList}")
    }
  }

  val system = ActorSystem("BackoffSupervisorDemo")
  //val simpleActor =
  //system.actorOf(Props[FileBasedPersistentActor], "simpleActor")
  //simpleActor ! ReadFile
  //if for some reasone the database stops working all actors will start restarting iteratively
  //it's for this that the backoff supervisor exists

  val simpleSupervisorProps = BackoffSupervisor.props(
    BackoffOpts.onFailure(
      Props[FileBasedPersistentActor],
      "simpleBackOffActor",
      3 seconds,
      30 seconds,
      0.2
    )
  )

  val simpleBackOffSupervisor =
    system.actorOf(simpleSupervisorProps, "simpleSupervisor")

  simpleBackOffSupervisor ! ReadFile

  /*
  simpleSupervisor
    - child called simpleBackoffActor (props of type FileBasedPersistentActor)
    - supervision strategy is the default one  (restarting on everything)
      -restarts on everything (as default)
        -1st restart will be after 3 secs
        -2nd restart will be after 6 seconds
      - 0.2 adds some noise to these intervals so that all actors are not booted at same time
   */

  val stopSupervisorProps = BackoffSupervisor.props(
    BackoffOpts
      .onStop(
        Props[FileBasedPersistentActor],
        "stopBackOffActor",
        3 seconds,
        30 seconds,
        0.2
      )
      .withSupervisorStrategy(
        OneForOneStrategy() {
          case _ => Stop
        }
      )
  )

  val stopSupervisor = system.actorOf(stopSupervisorProps, "stopSupervisor")
  stopSupervisor ! ReadFile

  class EagerFBPActor extends FileBasedPersistentActor {
    override def preStart(): Unit = {
      log.info("Eager actor starting")
      dataSource = Source.fromFile(
        new File(
          "src/main/resources/testFiles/important_data.txt"
        ) //this causes FileNotFound
      )
      //new File("src/main/resources/testFiles/important.txt")
    }
  }

  val eagerActor = system.actorOf(Props[EagerFBPActor])
  //By default, if ActorInitializionException the actors don't restart, they stop

  val repeatedSupervisorProps = BackoffSupervisor.props(
    BackoffOpts.onStop(
      Props[EagerFBPActor],
      "eagerFBPActor",
      1 second,
      30 seconds,
      0.1
    )
  )

  val repeatedSupervisor =
    system.actorOf(repeatedSupervisorProps, "eagerSupervisor")

  /*
  eager supervisor
    -child eager
       -will die on start with ActorInitializationException
   */

  Thread.sleep(10000)
  system.terminate()
}

package pl.touk.esp.engine.management

import java.util.concurrent.TimeoutException

import akka.pattern.AskTimeoutException
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.client.program.{ClusterClient, PackagedProgram, StandaloneClusterClient}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.akka.AkkaUtils
import org.apache.flink.runtime.client.JobClient
import org.apache.flink.runtime.instance.ActorGateway
import org.apache.flink.runtime.util.LeaderRetrievalUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.Try


class DefaultFlinkGateway(config: Configuration, timeout: FiniteDuration) extends FlinkGateway with LazyLogging {

  implicit val ec = ExecutionContext.Implicits.global

  var actorSystem = JobClient.startJobClientActorSystem(config)

  var gateway: ActorGateway = prepareGateway(config)

  var client: StandaloneClusterClient = createClient()

  override def invokeJobManager[Response: ClassTag](req: AnyRef): Future[Response] = {
    gateway.ask(req, timeout)
      .mapTo[Response]
  }

  override def run(program: PackagedProgram): Unit = {
    logger.debug(s"Deploying right now. PackagedProgram args :${program.getArguments}")
    val submissionResult = client.run(program, -1)
    logger.debug(s"Deployment finished. JobId ${submissionResult.getJobID}")
  }

  def shutDown(): Unit = {
    actorSystem.shutdown()
    actorSystem.awaitTermination(timeout)
    client.shutdown()
  }

  private def createClient() =
    new StandaloneClusterClient(config) {
      setDetached(true)

      //TODO: czy to jest dobry sposob?
      override def getJobManagerGateway = gateway
    }

  private def prepareGateway(config: Configuration): ActorGateway = {
    val timeout = AkkaUtils.getClientTimeout(config)
    val leaderRetrievalService = LeaderRetrievalUtils.createLeaderRetrievalService(config)
    LeaderRetrievalUtils.retrieveLeaderGateway(leaderRetrievalService, actorSystem, timeout)
  }



}

//TODO: tak w sumie to przydaloby sie to zrobic na aktorach w ui moze??
class RestartableFlinkGateway(prepareGateway: () => FlinkGateway) extends FlinkGateway with LazyLogging {

  implicit val ec = ExecutionContext.Implicits.global

  @volatile var gateway: FlinkGateway = null

  override def invokeJobManager[Response: ClassTag](req: AnyRef): Future[Response] = {
    tryToInvokeJobManager[Response](req)
      .recoverWith {
        case e: AskTimeoutException =>
          logger.error("Failed to connect to Flink, restarting", e)
          restart()
          tryToInvokeJobManager[Response](req)
      }
  }

  private def tryToInvokeJobManager[Response: ClassTag](req: AnyRef): Future[Response] = {
    retrieveGateway().invokeJobManager[Response](req)
  }

  override def run(program: PackagedProgram): Unit = {
    tryToRunProgram(program).recover {
      //TODO: jaki powinien byc ten wyjatek??
      case e: TimeoutException =>
        logger.error("Failed to connect to Flink, restarting", e)
        restart()
        tryToRunProgram(program)
    }.get
  }

  private def tryToRunProgram(program: PackagedProgram): Try[Unit] =
    Try(retrieveGateway().run(program))

  private def restart(): Unit = synchronized {
    shutDown()
    retrieveGateway()
  }

  def retrieveGateway() = synchronized {
    if (gateway == null) {
      logger.info("Creating new gateway")
      gateway = prepareGateway()
      logger.info("Gateway created")
    }
    gateway
  }

  override def shutDown() = synchronized {
    Option(gateway).foreach(_.shutDown())
    gateway = null
    logger.info("Gateway shut down")
  }
}

trait FlinkGateway {
  def invokeJobManager[Response: ClassTag](req: AnyRef): Future[Response]

  def run(program: PackagedProgram): Unit

  def shutDown(): Unit
}

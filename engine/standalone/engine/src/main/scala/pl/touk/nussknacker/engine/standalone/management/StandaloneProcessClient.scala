package pl.touk.nussknacker.engine.standalone.management

import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, ProcessStateConfigurator, ProcessStateCustomConfigurator, StateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import sttp.client.circe._
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.engine.sttp.SttpJson.asOptionalJson
import sttp.model.Uri

import scala.concurrent.{ExecutionContext, Future}

object StandaloneProcessClient {

  def apply(config: Config) : StandaloneProcessClient = {
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

    val managementUrls = config.getString("managementUrl").split(",").map(_.trim).toList
    val clients = managementUrls.map(new DispatchStandaloneProcessClient(_))
    new MultiInstanceStandaloneProcessClient(clients)
  }

}

trait StandaloneProcessClient {

  def deploy(deploymentData: DeploymentData): Future[Unit]

  def cancel(name: ProcessName): Future[Unit]

  def findStatus(name: ProcessName): Future[Option[ProcessState]]

  def processStateConfigurator: ProcessStateConfigurator

}

//this is v. simple approach - we accept inconsistent state on different nodes,
//but we're making user aware of problem and let him/her fix it
class MultiInstanceStandaloneProcessClient(clients: List[StandaloneProcessClient]) extends StandaloneProcessClient with LazyLogging {

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  override def deploy(deploymentData: DeploymentData): Future[Unit] = {
    Future.sequence(clients.map(_.deploy(deploymentData))).map(_ => ())
  }

  override def cancel(name: ProcessName): Future[Unit] = {
    Future.sequence(clients.map(_.cancel(name))).map(_ => ())
  }

  override def findStatus(name: ProcessName): Future[Option[ProcessState]] = {
    Future.sequence(clients.map(_.findStatus(name))).map { statuses =>
      statuses.distinct match {
        case `None`::Nil => None
        case Some(status)::Nil => Some(status)
        case a =>
          //TODO: more precise information
          logger.warn(s"Inconsistent states found: $a")
          val warningMessage = a.map {
            case None => "empty"
            case Some(state) => s"state: ${state.status}, startTime: ${state.startTime.getOrElse(None)}"
          }.mkString("; ")
          Some(ProcessState(
            DeploymentId(name.value),
            StateStatus.Failed,
            allowedActions = processStateConfigurator.getStatusActions(StateStatus.Failed),
            errorMessage = Some(s"Inconsistent states between servers: $warningMessage")
          ))
      }
    }
  }

  override def processStateConfigurator: ProcessStateConfigurator = ProcessStateCustomConfigurator
}

class DispatchStandaloneProcessClient(managementUrl: String)(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends StandaloneProcessClient {

  private val managementUri = Uri.parse(managementUrl).get

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def deploy(deploymentData: DeploymentData): Future[Unit] = {
    basicRequest
      .post(managementUri.path("deploy"))
      .body(deploymentData)
      .send()
      .map(_ => ())
  }

  def cancel(processName: ProcessName): Future[Unit] = {
    basicRequest
      .post(managementUri.path("cancel", processName.value))
      .send()
      .map(_ => ())
  }

  def findStatus(name: ProcessName): Future[Option[ProcessState]] = {
    basicRequest
      .get(managementUri.path("checkStatus", name.value))
      .response(asOptionalJson[ProcessState])
      .send()
      .flatMap(SttpJson.failureToFuture)
  }

  override def processStateConfigurator: ProcessStateConfigurator = ProcessStateCustomConfigurator
}


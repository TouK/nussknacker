package pl.touk.nussknacker.engine.requestresponse.management

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ExternalDeploymentId, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponseDeploymentData
import pl.touk.nussknacker.engine.requestresponse.deployment.DeploymentStatus
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.engine.sttp.SttpJson.asOptionalJson
import sttp.client._
import sttp.client.circe._
import sttp.model.StatusCode

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object RequestResponseClient {

  def apply(config: Config)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT]): RequestResponseClient = {
    val managementUrls = config.getString("managementUrl").split(",").map(_.trim).toList
    val clients = managementUrls.map(new HttpRequestResponseClient(_))
    new MultiInstanceRequestResponseClient(clients)
  }

}

trait RequestResponseClient extends AutoCloseable {

  def deploy(deploymentData: RequestResponseDeploymentData): Future[Unit]

  def cancel(name: ProcessName): Future[Unit]

  def findStatus(name: ProcessName): Future[Option[ProcessState]]

}

//this is v. simple approach - we accept inconsistent state on different nodes,
//but we're making user aware of problem and let him/her fix it
class MultiInstanceRequestResponseClient(clients: List[RequestResponseClient])(implicit ec: ExecutionContext) extends RequestResponseClient with LazyLogging {

  override def deploy(deploymentData: RequestResponseDeploymentData): Future[Unit] = {
    Future.sequence(clients.map(_.deploy(deploymentData))).map(_ => ())
  }

  override def cancel(name: ProcessName): Future[Unit] = {
    Future.sequence(clients.map(_.cancel(name))).map(_ => ())
  }

  override def findStatus(name: ProcessName): Future[Option[ProcessState]] = {
    Future.sequence(clients.map(_.findStatus(name))).map { statuses =>
      statuses.distinct match {
        case `None` :: Nil => None
        case Some(status) :: Nil => Some(status)
        case a =>
          //TODO: more precise information
          logger.warn(s"Inconsistent states found: $a")
          val warningMessage = a.map {
            case None => "empty"
            case Some(state) => s"state: ${state.status.name}, startTime: ${state.startTime.getOrElse(None)}"
          }.mkString("; ")
          Some(SimpleProcessState(
            ExternalDeploymentId(name.value),
            SimpleStateStatus.Failed,
            errors = List(s"Inconsistent states between servers: $warningMessage.")
          ))
      }
    }
  }

  override def close(): Unit = {
    clients.foreach(_.close())
  }
}

class HttpRequestResponseClient(managementUrl: String)(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends RequestResponseClient {

  import HttpClientErrorHandler._

  private val managementUri = uri"$managementUrl"

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def deploy(deploymentData: RequestResponseDeploymentData): Future[Unit] = {
    basicRequest
      .post(managementUri.path("deploy"))
      .body(deploymentData)
      .send()
      .flatMap(handleUnitResponse("deploy scenario"))
      .recoverWith(recoverWithMessage("deploy scenario"))
  }

  def cancel(processName: ProcessName): Future[Unit] = {
    basicRequest
      .post(managementUri.path("cancel", processName.value))
      .send()
      .flatMap(handleUnitResponse("cancel scenario"))
      .recoverWith(recoverWithMessage("cancel scenario"))
  }

  def findStatus(name: ProcessName): Future[Option[ProcessState]] = {
    basicRequest
      .get(managementUri.path("checkStatus", name.value))
      .response(asOptionalJson[DeploymentStatus])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .map(_.map { case DeploymentStatus(processVersion, deploymentTime) =>
        SimpleProcessState(
          deploymentId = ExternalDeploymentId(name.value),
          status = SimpleStateStatus.Running,
          version = Option(processVersion),
          startTime = Some(deploymentTime)
        )
      })
  }

  override def close(): Unit = Await.result(backend.close(), Duration(10, TimeUnit.SECONDS))

}


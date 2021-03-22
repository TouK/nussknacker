package pl.touk.nussknacker.engine.standalone.management

import java.util.concurrent.TimeUnit

import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ExternalDeploymentId, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.standalone.api.StandaloneDeploymentData
import sttp.client.circe._
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.engine.sttp.SttpJson.asOptionalJson
import sttp.model.Uri

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object StandaloneProcessClient {

  def apply(config: Config) : StandaloneProcessClient = {
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

    val managementUrls = config.getString("managementUrl").split(",").map(_.trim).toList
    val clients = managementUrls.map(new HttpStandaloneProcessClient(_))
    new MultiInstanceStandaloneProcessClient(clients)
  }

}

trait StandaloneProcessClient extends AutoCloseable {

  def deploy(deploymentData: StandaloneDeploymentData): Future[Unit]

  def cancel(name: ProcessName): Future[Unit]

  def findStatus(name: ProcessName): Future[Option[ProcessState]]

}

//this is v. simple approach - we accept inconsistent state on different nodes,
//but we're making user aware of problem and let him/her fix it
class MultiInstanceStandaloneProcessClient(clients: List[StandaloneProcessClient]) extends StandaloneProcessClient with LazyLogging {

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  override def deploy(deploymentData: StandaloneDeploymentData): Future[Unit] = {
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

class HttpStandaloneProcessClient(managementUrl: String)(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends StandaloneProcessClient {

  private val managementUri = uri"$managementUrl"

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def deploy(deploymentData: StandaloneDeploymentData): Future[Unit] = {
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

  override def close(): Unit = Await.result(backend.close(), Duration(10, TimeUnit.SECONDS))

}


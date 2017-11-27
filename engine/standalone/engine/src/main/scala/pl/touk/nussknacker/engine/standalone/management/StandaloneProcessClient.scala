package pl.touk.nussknacker.engine.standalone.management

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dispatch.Http
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.util.service.{AuditDispatchClient, LogCorrelationId}

import scala.concurrent.{ExecutionContext, Future}

object StandaloneProcessClient {

  def apply(config: Config) : StandaloneProcessClient = {
    val standaloneConf = config.getConfig("standaloneConfig")
    val managementUrls = standaloneConf.getString("managementUrl").split(",").map(_.trim).toList
    val clients = managementUrls.map(new DispatchStandalonProcessClient(_))
    new MultiInstanceStandaloneProcessClient(clients)
  }

}

trait StandaloneProcessClient {

  def deploy(deploymentData: DeploymentData): Future[Unit]

  def cancel(name: String): Future[Unit]

  def findStatus(name: String): Future[Option[ProcessState]]

}

//this is v. simple approach - we accept inconsistent state on different nodes,
//but we're making user aware of problem and let him/her fix it
class MultiInstanceStandaloneProcessClient(clients: List[StandaloneProcessClient]) extends StandaloneProcessClient with LazyLogging {

  private implicit val ec = ExecutionContext.Implicits.global


  override def deploy(deploymentData: DeploymentData): Future[Unit] = {
    Future.sequence(clients.map(_.deploy(deploymentData))).map(_ => ())
  }

  override def cancel(name: String): Future[Unit] = {
    Future.sequence(clients.map(_.cancel(name))).map(_ => ())
  }

  override def findStatus(name: String): Future[Option[ProcessState]] = {
    Future.sequence(clients.map(_.findStatus(name))).map { statuses =>
      statuses.distinct match {
        case None::Nil => None
        case Some(status)::Nil => Some(status)
        case a =>
          //TODO: more precise information
          logger.warn(s"Inconsistent states found: $a")
          Some(ProcessState(name, "INCONSISTENT", 0L))
      }
    }
  }

}

class DispatchStandalonProcessClient(managementUrl: String, http: Http = Http) extends StandaloneProcessClient {

  private implicit val ec = ExecutionContext.Implicits.global

  private val httpClient = Http()
  private val dispatchClient = new AuditDispatchClient {
    override protected def http: Http = httpClient
  }

  import argonaut.ArgonautShapeless._

  def deploy(deploymentData: DeploymentData): Future[Unit] = {
    implicit val correlationId = LogCorrelationId(UUID.randomUUID().toString)
    val deployUrl = dispatch.url(managementUrl) / "deploy"
    dispatchClient.postObjectAsJsonWithoutResponseParsing(deployUrl, deploymentData).map(_ => ())
  }

  def cancel(name: String): Future[Unit] = {
    implicit val correlationId = LogCorrelationId(UUID.randomUUID().toString)
    val cancelUrl = dispatch.url(managementUrl) / "cancel" / name
    dispatchClient.sendWithAuditAndStatusChecking(cancelUrl.POST).map(_ => ())
  }

  def findStatus(name: String): Future[Option[ProcessState]] = {
    implicit val correlationId = LogCorrelationId(UUID.randomUUID().toString)
    val jobStatusUrl = dispatch.url(managementUrl) / "checkStatus" / name
    dispatchClient.getPossiblyUnavailableJsonAsObject[ProcessState](jobStatusUrl)
  }

}


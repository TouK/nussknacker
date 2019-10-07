package pl.touk.nussknacker.engine.standalone.management

import argonaut.CodecJson
import org.asynchttpclient.Response
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dispatch.{Http, StatusCode}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, RunningState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.dispatch.LoggingDispatchClient
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.util.json.Codecs

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object StandaloneProcessClient {

  def apply(config: Config) : StandaloneProcessClient = {
    val managementUrls = config.getString("managementUrl").split(",").map(_.trim).toList
    val clients = managementUrls.map(new DispatchStandaloneProcessClient(_))
    new MultiInstanceStandaloneProcessClient(clients)
  }

}

trait StandaloneProcessClient {

  def deploy(deploymentData: DeploymentData): Future[Unit]

  def cancel(name: ProcessName): Future[Unit]

  def findStatus(name: ProcessName): Future[Option[ProcessState]]

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
            case Some(state) => s"state: ${state.status}, startTime: ${state.startTime}"
          }.mkString("; ")
          Some(ProcessState(DeploymentId(name.value), runningState = RunningState.Error, "INCONSISTENT", 0L, None,
            message = Some(s"Inconsistent states between servers: $warningMessage")))
      }
    }
  }

}

class DispatchStandaloneProcessClient(managementUrl: String, http: Http = Http.default) extends StandaloneProcessClient {


  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  import pl.touk.nussknacker.engine.dispatch.utils._
  private val dispatchClient = LoggingDispatchClient(this.getClass.getSimpleName, http)

  import argonaut.ArgonautShapeless._
  import ProcessName.codec
  private implicit val stateCodec: CodecJson[RunningState.Value] = Codecs.enumCodec(RunningState)

  def deploy(deploymentData: DeploymentData): Future[Unit] = {
    val deployUrl = dispatch.url(managementUrl) / "deploy"
    dispatchClient {
      postJson  (deployUrl, deploymentData) OK asUnit
    }
  }

  def cancel(processName: ProcessName): Future[Unit] = {
    val cancelUrl = dispatch.url(managementUrl) / "cancel" / processName.value
    dispatchClient {
      cancelUrl.POST OK asUnit
    }
  }

  def findStatus(name: ProcessName): Future[Option[ProcessState]] = {
    def notFoundHandler(r: Response): Option[ProcessState] = {
      if (r.getStatusCode == 404)
        None
      else if (r.getStatusCode / 100 == 2)
        Some(asJson[ProcessState](r))
      else
        throw StatusCode(r.getStatusCode)
    }

    val jobStatusUrl = dispatch.url(managementUrl) / "checkStatus" / name.value
    dispatchClient {
      jobStatusUrl > notFoundHandler _
    }
  }

}


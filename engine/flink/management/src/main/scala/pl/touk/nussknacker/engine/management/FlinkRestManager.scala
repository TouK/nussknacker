package pl.touk.nussknacker.engine.management

import java.io.File

import argonaut._
import Argonaut._
import ArgonautShapeless._
import com.ning.http.client.multipart.FilePart
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dispatch._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.dispatch.{LoggingDispatchClient, utils}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.management.flinkRestModel.{DeployProcessRequest, GetSavepointStatusResponse, JobsResponse, SavepointTriggerResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object FlinkRestManager {

  def apply(config: Config): FlinkRestManager = {
    val flinkConfig = config.as[FlinkConfig]("flinkConfig")
    new FlinkRestManager(flinkConfig, FlinkModelData(config))
  }

}

class FlinkRestManager(config: FlinkConfig, modelData: ModelData) extends FlinkProcessManager(modelData, config.shouldVerifyBeforeDeploy.getOrElse(true)) with LazyLogging {

  private val httpClient = LoggingDispatchClient(classOf[FlinkRestManager].getSimpleName,
    //we have to follow redirects to be able to use HA mode
    Http.configure(_
      .setAllowPoolingConnections(true)
      .setConnectionTTL(30000)
      .setFollowRedirect(true))
  )

  private val flinkUrl = dispatch.url(config.restUrl)

  private lazy val uploadedJarId : Future[String] = uploadCurrentJar()

  private def uploadCurrentJar(): Future[String] = {
    val filePart = new FilePart("jarfile", jarFile, "application/x-java-archive")
    httpClient {
      (flinkUrl / "jars" / "upload").POST.addBodyPart(filePart) OK utils.asJson[Json]
    } map { json =>
      new File(json.fieldOrEmptyString("filename").stringOrEmpty).getName
    }
  }


  override def findJobStatus(name: String): Future[Option[ProcessState]] = {
    httpClient {
      (flinkUrl / "jobs" / "overview").GET OK utils.asJson[JobsResponse]
    } map { jobs =>
      jobs
        .jobs
        .sortBy(j => - j.`last-modification`)
        .find(_.name == name)
        .map(j => ProcessState(j.jid, j.state, j.`start-time`))
        //TODO: needed?
        .filterNot(_.status == "CANCELED")
    }
  }

  //FIXME: get rid of sleep, refactor?
  private def waitForSavepoint(jobId: String, savepointId: String, timeoutLeft: Long = config.jobManagerTimeout.toMillis): Future[String] = {
    val start = System.currentTimeMillis()
    if (timeoutLeft <= 0) {
      return Future.failed(new Exception(s"Failed to complete savepoint in time for $jobId and trigger $savepointId"))
    }
    httpClient {
      (flinkUrl / "jobs"/ jobId / "savepoints" / savepointId).GET OK utils.asJson[GetSavepointStatusResponse]
    }.flatMap { resp =>
      resp.operation.flatMap(_.location) match {
        case Some(location) if resp.status.isCompleted =>
          Future.successful(location)
        case _ =>
          Thread.sleep(1000)
          waitForSavepoint(jobId, savepointId, timeoutLeft - (System.currentTimeMillis() - start))

      }
    }
  }

  override protected def cancel(job: ProcessState): Future[Unit] = {
    httpClient {
      (flinkUrl / "jobs" / job.id).PATCH OK (_ => ())
    }
  }

  override protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[String] = {
    httpClient {
      (flinkUrl / "jobs" / job.id / "savepoints").POST.setBody("""{"cancel-job": false}""") OK utils.asJson[SavepointTriggerResponse]
    }.flatMap { response =>
      waitForSavepoint(job.id, response.`request-id`)
    }
  }


  override protected def runProgram(processId: String, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit] = {
    val program =
      DeployProcessRequest(entryClass = mainClass, parallelism = ExecutionConfig.PARALLELISM_DEFAULT, savepointPath = savepointPath,
        programArgs = FlinkArgsEncodeHack.prepareProgramArgs(args).mkString(" "))
    uploadedJarId.flatMap { jarId =>
     httpClient {
        (flinkUrl / "jars" / jarId / "run").POST.setBody(program.asJson.spaces2) OK (_ => ())
     }
    }
  }

}

object flinkRestModel {

  case class DeployProcessRequest(entryClass: String, parallelism: Int, savepointPath: Option[String], programArgs: String)

  case class SavepointTriggerResponse(`request-id`: String)

  case class GetSavepointStatusResponse(status: SavepointStatus, operation: Option[SavepointOperation])

  case class SavepointOperation(location: Option[String])

  case class SavepointStatus(id: String) {
    val isCompleted: Boolean = id == "COMPLETED"
  }

  case class JobsResponse(jobs: List[JobOverview])

  case class JobOverview(jid: String, name: String, `last-modification`: Long, `start-time`: Long, state: String)

}

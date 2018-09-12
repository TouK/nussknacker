package pl.touk.nussknacker.engine.management

import java.io.File

import argonaut._
import Argonaut._
import ArgonautShapeless._
import com.ning.http.client.{AsyncCompletionHandler, Request, RequestBuilder}
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
import scala.concurrent.{ExecutionContext, Future}


object FlinkRestManager {

  def apply(config: Config): FlinkRestManager = {
    val flinkConfig = config.as[FlinkConfig]("flinkConfig")
    new FlinkRestManager(flinkConfig, FlinkModelData(config))
  }

}

class FlinkRestManager(config: FlinkConfig, modelData: ModelData) extends FlinkProcessManager(modelData, config.shouldVerifyBeforeDeploy.getOrElse(true)) with LazyLogging {

  private val httpClient = LoggingDispatchClient(classOf[FlinkRestManager].getSimpleName, Http)

  private val flinkUrl = dispatch.url(config.restUrl)

  //We handle redirections manually, as dispatch/asynchttpclient has some problems (connection leaking with it in our use cases)
  private def send[T](pair: (Request, FunctionHandler[T]))
                (implicit executor: ExecutionContext): Future[T] = {
    httpClient(pair._1, new AsyncCompletionHandler[Future[T]]() {

      override def onCompleted(response: Res): Future[T] = {
        if (response.getStatusCode / 100 == 3) {
          val newLocation = response.getHeader("LOCATION")
          val newRequest = new RequestBuilder(pair._1).setUrl(newLocation).build()
          httpClient(newRequest, pair._2)
        } else {
          Future.successful(pair._2.onCompleted(response))
        }
      }
    }).flatten

  }

  private lazy val uploadedJarId : Future[String] = uploadCurrentJar()

  private def uploadCurrentJar(): Future[String] = {
    val filePart = new FilePart("jarfile", jarFile, "application/x-java-archive")
    send {
      (flinkUrl / "jars" / "upload").POST.addBodyPart(filePart) OK utils.asJson[Json]
    } map { json =>
      new File(json.fieldOrEmptyString("filename").stringOrEmpty).getName
    }
  }


  override def findJobStatus(name: String): Future[Option[ProcessState]] = {
    send {
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
    send {
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
    send {
      (flinkUrl / "jobs" / job.id).PATCH OK (_ => ())
    }
  }

  override protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[String] = {
    send {
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
      send {
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
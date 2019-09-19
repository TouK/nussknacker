package pl.touk.nussknacker.engine.management

import java.io.File

import argonaut.Argonaut._
import argonaut._
import ArgonautShapeless._
import org.asynchttpclient.{AsyncCompletionHandler, Request, RequestBuilder}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dispatch._
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.runtime.jobgraph.JobStatus
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.dispatch.{LoggingDispatchClient, utils}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.ExecutionConfig
import org.asynchttpclient.request.body.multipart.FilePart
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.dispatch.{LoggingDispatchClient, utils}
import pl.touk.nussknacker.engine.management.flinkRestModel.{DeployProcessRequest, GetSavepointStatusResponse, JobsResponse, SavepointTriggerResponse, jobStatusDecoder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


private[management] trait HttpSender {
  def send[T](pair: (Request, FunctionHandler[T]))
             (implicit executor: ExecutionContext): Future[T]
}

private[management] object DefaultHttpSender extends HttpSender {

  private val httpClient = LoggingDispatchClient(classOf[FlinkRestManager].getSimpleName, Http.default)

  //We handle redirections manually, as dispatch/asynchttpclient has some problems (connection leaking with it in our use cases)
  override def send[T](pair: (Request, FunctionHandler[T]))
                      (implicit executor: ExecutionContext): Future[T] = {
    httpClient(pair._1, new AsyncCompletionHandler[Future[T]]() {

      override def onCompleted(response: Res): Future[T] = {
        if (response.getStatusCode / 100 == 3) {
          val newLocation = response.getHeader("LOCATION")
          val newRequest = new RequestBuilder(pair._1).setUrl(newLocation).build()
          httpClient(newRequest, pair._2)
        } else if (response.getStatusCode / 100 != 2) {
          Future.failed(new Exception(s"HTTP request failed with status code ${response.getStatusCode} and message: ${response.getStatusText}"))
        } else {
          Future.successful(pair._2.onCompleted(response))
        }
      }
    }).flatten
  }

}

class FlinkRestManager(config: FlinkConfig, modelData: ModelData, sender: HttpSender = DefaultHttpSender) extends FlinkProcessManager(modelData, config.shouldVerifyBeforeDeploy.getOrElse(true)) with LazyLogging {

  private val flinkUrl = dispatch.url(config.restUrl)

  // after job manager restart old resources are not available anymore and we have to upload jar once again
  private var jarUploadedBeforeLastRestart: Option[Future[String]] = None

  // this code is executed synchronously by ManagementActor thus we don't care that much about possible races
  // and extraneous jar uploads introduced by asynchronous invocation of recoverWith
  private def uploadedJarId(): Future[String] = jarUploadedBeforeLastRestart match {
    case None =>
      uploadCurrentJar()
    case Some(uploadedJar) =>
      uploadedJar
        .flatMap(checkIfJarExists)
        .recoverWith { case ex =>
          logger.info(s"Getting already uploaded jar failed with $ex, trying to upload again")
          uploadCurrentJar()
        }
  }

  private def checkIfJarExists(jarId: String): Future[String] = {
    sender.send {
      (flinkUrl / "jars").GET OK utils.asJson[Json]
    }.flatMap { json =>
      val isJarUploaded = json
        .fieldOrEmptyArray("files").arrayOrEmpty
        .exists(_.fieldOrEmptyString("id").stringOrEmpty == jarId)

      if (isJarUploaded) {
        Future.successful(jarId)
      } else {
        Future.failed(new Exception(s"Jar with id '$jarId' does not exist"))
      }
    }
  }

  private def uploadCurrentJar(): Future[String] = {
    logger.debug("Uploading new jar")
    val filePart = new FilePart("jarfile", jarFile, "application/x-java-archive")
    val uploadedJar = sender.send {
      (flinkUrl / "jars" / "upload").POST.addBodyPart(filePart) OK utils.asJson[Json]
    } map { json =>
      logger.info(s"Uploaded jar to $json")
      new File(json.fieldOrEmptyString("filename").stringOrEmpty).getName
    }
    jarUploadedBeforeLastRestart = Some(uploadedJar)
    uploadedJar
  }


  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = {
    sender.send {
      (flinkUrl / "jobs" / "overview").GET OK utils.asJson[JobsResponse]
    } map { jobs =>
      val jobsForName = jobs.jobs
        .filter(_.name == name.value)
        .sortBy(_.`last-modification`).reverse

      val runningOrFinished = jobsForName
        .filter(status => !status.state.isGloballyTerminalState || status.state == JobStatus.FINISHED)

      runningOrFinished match {
        case Nil => None
        case duplicates if duplicates.count(_.state == JobStatus.RUNNING) > 1 =>
          Some(ProcessState(DeploymentId(duplicates.head.jid), RunningState.Error, "INCONSISTENT", duplicates.head.`start-time`, None,
            Some(s"Expected one job, instead: ${runningOrFinished.map(job => s"${job.jid} - ${job.state.name()}").mkString(", ")}")))
        case one::_ =>
          val runningState = one.state match {
            case JobStatus.RUNNING => RunningState.Running
            case JobStatus.FINISHED => RunningState.Finished
            case _ => RunningState.Error
          }
          //TODO: extract version number from job config
          Some(ProcessState(DeploymentId(one.jid), runningState, one.state.toString, one.`start-time`, None))
      }
    }
  }

  //FIXME: get rid of sleep, refactor?
  private def waitForSavepoint(jobId: DeploymentId, savepointId: String, timeoutLeft: Long = config.jobManagerTimeout.toMillis): Future[String] = {
    val start = System.currentTimeMillis()
    if (timeoutLeft <= 0) {
      return Future.failed(new Exception(s"Failed to complete savepoint in time for $jobId and trigger $savepointId"))
    }
    sender.send {
      (flinkUrl / "jobs"/ jobId.value / "savepoints" / savepointId).GET OK utils.asJson[GetSavepointStatusResponse]
    }.flatMap { resp =>
      logger.debug(s"Waiting for savepoint $savepointId of $jobId, got response: $resp")
      if (resp.isCompletedSuccessfully) {
        //getOrElse is not really needed since isCompletedSuccessfully returns true only if it's defined
        val location = resp.operation.flatMap(_.location).getOrElse("")
        logger.info(s"Savepoint $savepointId for $jobId finished in $location")
        Future.successful(location)
      } else if (resp.isFailed) {
        Future.failed(new RuntimeException(s"Failed to complete savepoint: ${resp.operation}"))
      } else {
        Thread.sleep(1000)
        waitForSavepoint(jobId, savepointId, timeoutLeft - (System.currentTimeMillis() - start))
      }
    }
  }

  override protected def cancel(job: ProcessState): Future[Unit] = {
    sender.send {
      (flinkUrl / "jobs" / job.id.value).PATCH OK (_ => ())
    }
  }

  override protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[String] = {
    sender.send {
      (flinkUrl / "jobs" / job.id.value / "savepoints").POST.setBody("""{"cancel-job": false}""") OK utils.asJson[SavepointTriggerResponse]
    }.flatMap { response =>
      waitForSavepoint(job.id, response.`request-id`)
    }
  }


  override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit] = {
    val program =
      DeployProcessRequest(
        entryClass = mainClass,
        parallelism = ExecutionConfig.PARALLELISM_DEFAULT,
        savepointPath = savepointPath,
        allowNonRestoredState = true,
        programArgs = FlinkArgsEncodeHack.prepareProgramArgs(args).mkString(" "))
    logger.debug(s"Starting to deploy process: $processName with savepoint $savepointPath")
    uploadedJarId().flatMap { jarId =>
      logger.debug(s"Deploying $processName with $savepointPath and jarId: $jarId")
      sender.send {
        (flinkUrl / "jars" / jarId / "run").POST.setBody(program.asJson.spaces2) OK (_ => ())
     }
    }
  }

}

object flinkRestModel {

  implicit val jobStatusDecoder: DecodeJson[JobStatus] = DecodeJson.of[String].map(JobStatus.valueOf)

  case class DeployProcessRequest(entryClass: String, parallelism: Int, savepointPath: Option[String], programArgs: String, allowNonRestoredState: Boolean)

  case class SavepointTriggerResponse(`request-id`: String)

  case class GetSavepointStatusResponse(status: SavepointStatus, operation: Option[SavepointOperation]) {

    def isCompletedSuccessfully: Boolean = status.isCompleted && operation.flatMap(_.location).isDefined

    def isFailed: Boolean = status.isCompleted && !isCompletedSuccessfully

  }

  case class SavepointOperation(location: Option[String], `failure-cause`: Option[FailureCause])

  case class FailureCause(`class`: Option[String], `stack-trace`: Option[String], `serialized-throwable`: Option[String])

  case class SavepointStatus(id: String) {
    def isCompleted: Boolean = id == "COMPLETED"
  }

  case class JobsResponse(jobs: List[JobOverview])

  case class JobOverview(jid: String, name: String, `last-modification`: Long, `start-time`: Long, state: JobStatus)

}
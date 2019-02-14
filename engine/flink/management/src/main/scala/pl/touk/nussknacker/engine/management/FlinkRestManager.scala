package pl.touk.nussknacker.engine.management

import java.io.File

import argonaut._
import Argonaut._
import ArgonautShapeless._
import com.ning.http.client.{AsyncCompletionHandler, Request, RequestBuilder}
import com.ning.http.client.multipart.FilePart
import com.typesafe.scalalogging.LazyLogging
import dispatch._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.dispatch.{LoggingDispatchClient, utils}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.runtime.jobgraph.JobStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.flinkRestModel.{DeployProcessRequest, GetSavepointStatusResponse, JobsResponse, SavepointTriggerResponse, jobStatusDecoder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


private[management] trait HttpSender {
  def send[T](pair: (Request, FunctionHandler[T]))
                  (implicit executor: ExecutionContext): Future[T]
}

private[management] object DefaultHttpSender extends HttpSender {

  private val httpClient = LoggingDispatchClient(classOf[FlinkRestManager].getSimpleName, Http)

  //We handle redirections manually, as dispatch/asynchttpclient has some problems (connection leaking with it in our use cases)
  override def send[T](pair: (Request, FunctionHandler[T]))
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

}

class FlinkRestManager(config: FlinkConfig, modelData: ModelData, sender: HttpSender = DefaultHttpSender) extends FlinkProcessManager(modelData, config.shouldVerifyBeforeDeploy.getOrElse(true)) with LazyLogging {

  private val flinkUrl = dispatch.url(config.restUrl)

  private lazy val uploadedJarId : Future[String] = uploadCurrentJar()

  private def uploadCurrentJar(): Future[String] = {
    logger.debug("Uploading new jar")
    val filePart = new FilePart("jarfile", jarFile, "application/x-java-archive")
    sender.send {
      (flinkUrl / "jars" / "upload").POST.addBodyPart(filePart) OK utils.asJson[Json]
    } map { json =>
      logger.info(s"Uploaded jar to $json")
      new File(json.fieldOrEmptyString("filename").stringOrEmpty).getName
    }
  }


  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = {
    sender.send {
      (flinkUrl / "jobs" / "overview").GET OK utils.asJson[JobsResponse]
    } map { jobs =>
      val runningJobs = jobs
        .jobs
        .filter(_.name == name.value)
        .filterNot(_.state.isGloballyTerminalState)

      runningJobs match {
        case Nil => None
        case one::Nil =>
          val runningState = one.state match {
            case JobStatus.RUNNING => RunningState.Running
            case JobStatus.FINISHED => RunningState.Finished
            case _ => RunningState.Error
          }
          Some(ProcessState(DeploymentId(one.jid), runningState, one.state.toString, one.`start-time`))
        case one::rest =>
          Some(ProcessState(DeploymentId(one.jid), RunningState.Error, "INCONSISTENT", one.`start-time`,
            Some(s"Expected one job, instead: ${runningJobs.map(_.jid).mkString(", ")}")))
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
    uploadedJarId.flatMap { jarId =>
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
package pl.touk.nussknacker.engine.management

import java.io.File
import java.util.concurrent.TimeoutException

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.JsonCodec
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.runtime.jobgraph.JobStatus
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.flinkRestModel.{DeployProcessRequest, GetSavepointStatusResponse, JarsResponse, JobConfig, JobsResponse, SavepointTriggerRequest, SavepointTriggerResponse, StopRequest, UploadJarResponse}
import pl.touk.nussknacker.engine.sttp.SttpJson
import sttp.client._
import sttp.client.circe._
import sttp.model.Uri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class FlinkRestManager(config: FlinkConfig, modelData: ModelData, mainClassName: String)
                      (implicit backend: SttpBackend[Future, Nothing, NothingT])
    extends FlinkProcessManager(modelData, config.shouldVerifyBeforeDeploy.getOrElse(true), mainClassName) with LazyLogging {

  private val flinkUrl = Uri.parse(config.restUrl).get

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
    basicRequest
      .get(flinkUrl.path("jars"))
      .response(asJson[JarsResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .flatMap { jars =>
        val isJarUploaded = jars.files.toList.flatten.exists(_.id == jarId)
        if (isJarUploaded) {
          Future.successful(jarId)
        } else {
          Future.failed(new Exception(s"Jar with id '$jarId' does not exist"))
        }
    }
  }

  private def uploadCurrentJar(): Future[String] = {
    logger.debug("Uploading new jar")

    val uploadedJar = basicRequest
      .post(flinkUrl.path("jars", "upload"))
      .multipartBody( multipartFile("jarfile", jarFile).contentType("application/x-java-archive"))
      .response(asJson[UploadJarResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .map { file =>
        logger.info(s"Uploaded jar to $file")
        new File(file.filename).getName
    }
    jarUploadedBeforeLastRestart = Some(uploadedJar)
    uploadedJar
  }

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = {
    basicRequest
      .get(flinkUrl.path("jobs", "overview"))
      .response(asJson[JobsResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .flatMap { jobs =>

        val jobsForName = jobs.jobs
          .filter(_.name == name.value)
          .sortBy(_.`last-modification`)
          .reverse

        jobsForName match {
          case Nil => Future.successful(None)
          case duplicates if duplicates.count(_.state == JobStatus.RUNNING) > 1 =>
            Future.successful(Some(ProcessState(
              DeploymentId(duplicates.head.jid),
              FlinkStateStatus.Failed,
              definitionManager = processStateDefinitionManager,
              version = Option.empty,
              attributes = Option.empty,
              startTime = Some(duplicates.head.`start-time`),
              errors = List(s"Expected one job, instead: ${jobsForName.map(job => s"${job.jid} - ${job.state.name()}").mkString(", ")}"))
            ))
          case one::_ =>
            val stateStatus = one.state match {
              case JobStatus.RUNNING => FlinkStateStatus.Running
              case JobStatus.FINISHED => FlinkStateStatus.Finished
              case JobStatus.RESTARTING => FlinkStateStatus.Restarting
              case JobStatus.CANCELED => FlinkStateStatus.Canceled
              case JobStatus.CANCELLING => FlinkStateStatus.DuringCancel
              case _ => FlinkStateStatus.Failed
            }
            checkVersion(one.jid, name).map { version =>
              //TODO: return error when there's no correct version in process
              //currently we're rather lax on this, so that this change is backward-compatible
              //we log debug here for now, since it's invoked v. often
              if (version.isEmpty) {
                logger.debug(s"No correct version in deployed process: ${one.name}")
              }

              Some(ProcessState(
                DeploymentId(one.jid),
                stateStatus,
                version = version,
                definitionManager = processStateDefinitionManager,
                startTime = Some(one.`start-time`),
                attributes = Option.empty,
                errors = List.empty
              ))
            }
        }
      }
  }

  //TODO: cache by jobId?
  private def checkVersion(jobId: String, name: ProcessName): Future[Option[ProcessVersion]] = {
    basicRequest
      .get(flinkUrl.path("jobs", jobId, "config"))
      .response(asJson[JobConfig])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .map { config =>
        val userConfig = config.`execution-config`.`user-config`
        for {
          version <- userConfig.get("versionId").flatMap(_.asString).map(_.toLong)
          user <- userConfig.get("user").map(_.asString.getOrElse(""))
          modelVersion = userConfig.get("modelVersion").flatMap(_.asString).map(_.toInt)
        } yield {
          ProcessVersion(version, name, user, modelVersion)
        }
      }
  }

  //FIXME: get rid of sleep, refactor?
  private def waitForSavepoint(jobId: DeploymentId, savepointId: String, timeoutLeft: Long = config.jobManagerTimeout.toMillis): Future[SavepointResult] = {
    val start = System.currentTimeMillis()
    if (timeoutLeft <= 0) {
      return Future.failed(new Exception(s"Failed to complete savepoint in time for $jobId and trigger $savepointId"))
    }
    basicRequest
      .get(flinkUrl.path("jobs", jobId.value, "savepoints", savepointId))
      .response(asJson[GetSavepointStatusResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .flatMap { resp =>
      logger.debug(s"Waiting for savepoint $savepointId of $jobId, got response: $resp")
      if (resp.isCompletedSuccessfully) {
        //getOrElse is not really needed since isCompletedSuccessfully returns true only if it's defined
        val location = resp.operation.flatMap(_.location).getOrElse("")
        logger.info(s"Savepoint $savepointId for $jobId finished in $location")
        Future.successful(SavepointResult(location))
      } else if (resp.isFailed) {
        Future.failed(new RuntimeException(s"Failed to complete savepoint: ${resp.operation}"))
      } else {
        Thread.sleep(1000)
        waitForSavepoint(jobId, savepointId, timeoutLeft - (System.currentTimeMillis() - start))
      }
    }
  }

  override protected def cancel(job: ProcessState): Future[Unit] = {
    basicRequest
      .patch(flinkUrl.path("jobs", job.deploymentId.value))
      .send()
      .flatMap(handleUnitResponse)
  }

  override protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[SavepointResult] = {
    val savepointRequest = basicRequest
      .post(flinkUrl.path("jobs", job.deploymentId.value, "savepoints"))
      .body(SavepointTriggerRequest(`target-directory` = savepointDir, `cancel-job` = false))
    processSavepointRequest(job, savepointRequest)
  }

  override protected def stop(job: ProcessState, savepointDir: Option[String]): Future[SavepointResult] = {
    val stopRequest = basicRequest
      .post(flinkUrl.path("jobs", job.deploymentId.value, "stop"))
      .body(StopRequest(targetDirectory = savepointDir, drain = false))
    processSavepointRequest(job, stopRequest)
  }

  private def processSavepointRequest(job: ProcessState, request: RequestT[Identity, Either[String, String], Nothing]): Future[SavepointResult] = {
    request
      .response(asJson[SavepointTriggerResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .flatMap { response =>
        waitForSavepoint(job.deploymentId, response.`request-id`)
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
      basicRequest
        .post(flinkUrl.path("jars", jarId, "run"))
        .body(program)
        .send()
        .flatMap(handleUnitResponse)
        .recover({
          //sometimes deploying takes too long, which causes TimeoutException while waiting for deploy response
          //workaround for now, not the best solution though
          //TODO: we should change logic of ManagementActor to always save action deploy
          case _: TimeoutException => {
            logger.warn("TimeoutException occurred while waiting for deploy result. Recovering with Future.successful...")
            Future.successful(Unit)
          }
        })
    }
  }

  private def handleUnitResponse(response: Response[Either[String, String]]): Future[Unit] = response.body match {
    case Right(_) => Future.successful(())
    case Left(error) => Future.failed(new RuntimeException(s"Request failed: $error, code: ${response.code}"))
  }
}

object flinkRestModel {

  implicit val jobStatusDecoder: Decoder[JobStatus] = Decoder.decodeString.map(JobStatus.valueOf)

  /*
  When #programArgsList is not set in request Flink warns that
  <pre>Configuring the job submission via query parameters is deprecated. Please migrate to submitting a JSON request instead.</pre>
  But now we can't add #programArgsList support because of back compatibility of Flink 1.6..
   */
  @JsonCodec(encodeOnly = true) case class DeployProcessRequest(entryClass: String, parallelism: Int, savepointPath: Option[String], programArgs: String, allowNonRestoredState: Boolean)

  @JsonCodec(encodeOnly = true) case class SavepointTriggerRequest(`target-directory`: Option[String], `cancel-job`: Boolean)

  @JsonCodec(encodeOnly = true) case class StopRequest(targetDirectory: Option[String], drain: Boolean)

  @JsonCodec(decodeOnly = true) case class SavepointTriggerResponse(`request-id`: String)

  @JsonCodec(decodeOnly = true) case class GetSavepointStatusResponse(status: SavepointStatus, operation: Option[SavepointOperation]) {

    def isCompletedSuccessfully: Boolean = status.isCompleted && operation.flatMap(_.location).isDefined

    def isFailed: Boolean = status.isCompleted && !isCompletedSuccessfully

  }

  @JsonCodec(decodeOnly = true) case class SavepointOperation(location: Option[String], `failure-cause`: Option[FailureCause])

  @JsonCodec(decodeOnly = true) case class FailureCause(`class`: Option[String], `stack-trace`: Option[String], `serialized-throwable`: Option[String])

  @JsonCodec(decodeOnly = true) case class SavepointStatus(id: String) {
    def isCompleted: Boolean = id == "COMPLETED"
  }

  @JsonCodec(decodeOnly = true) case class JobsResponse(jobs: List[JobOverview])

  @JsonCodec(decodeOnly = true) case class JobOverview(jid: String, name: String, `last-modification`: Long, `start-time`: Long, state: JobStatus)

  @JsonCodec(decodeOnly = true) case class JobConfig(jid: String, `execution-config`: ExecutionConfig)

  @JsonCodec(decodeOnly = true) case class ExecutionConfig(`user-config`: Map[String, io.circe.Json])

  @JsonCodec(decodeOnly = true) case class JarsResponse(files: Option[List[JarFile]])

  @JsonCodec(decodeOnly = true) case class UploadJarResponse(filename: String)

  @JsonCodec(decodeOnly = true) case class JarFile(id: String)
}

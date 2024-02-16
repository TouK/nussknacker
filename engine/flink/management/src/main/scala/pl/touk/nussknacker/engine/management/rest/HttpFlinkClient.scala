package pl.touk.nussknacker.engine.management.rest

import cats.data.Validated.{invalid, valid}
import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, SavepointResult, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.rest.flinkRestModel._
import pl.touk.nussknacker.engine.management.{FlinkArgsEncodeHack, FlinkConfig}
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.engine.util.exception.DeeplyCheckingExceptionExtractor
import sttp.client3._
import sttp.client3.circe._
import sttp.model.Uri

import java.io.File
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class HttpFlinkClient(config: FlinkConfig, flinkUrl: Uri)(
    implicit backend: SttpBackend[Future, Any],
    ec: ExecutionContext
) extends FlinkClient
    with LazyLogging {

  import pl.touk.nussknacker.engine.sttp.HttpClientErrorHandler._

  def uploadJarFileIfNotExists(jarFile: File): Future[JarFile] = {
    checkThatJarWithNameExists(jarFile.getName).flatMap {
      case Some(file) => Future.successful(file)
      case None       => uploadJar(jarFile).map(id => JarFile(id, jarFile.getName))
    }
  }

  private def checkThatJarWithNameExists(jarName: String): Future[Option[JarFile]] = {
    basicRequest
      .get(flinkUrl.addPath("jars"))
      .response(asJson[JarsResponse])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .map(_.files.toList.flatten.find(_.name == jarName))
  }

  def uploadJar(jarFile: File): Future[String] = {
    logger.debug(s"Uploading new jar: ${jarFile.getAbsolutePath}")
    basicRequest
      .post(flinkUrl.addPath("jars", "upload"))
      .multipartBody(multipartFile("jarfile", jarFile).contentType("application/x-java-archive"))
      .response(asJson[UploadJarResponse])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .map { file =>
        logger.info(s"Uploaded jar to $file")
        new File(file.filename).getName
      }
      .recoverWith(recoverWithMessage("upload Nussnknacker jar to Flink"))
  }

  override def deleteJarIfExists(jarFileName: String): Future[Unit] = {
    checkThatJarWithNameExists(jarFileName).flatMap {
      case Some(file) =>
        deleteJar(file.id).recover { case ex =>
          logger.warn(s"Failed to delete jar: $jarFileName", ex)
          ()
        }
      case None =>
        logger.info(s"$jarFileName does not exist, not removing")
        Future.successful(())
    }
  }

  private def deleteJar(jarId: String): Future[Unit] = {
    logger.info(s"Delete jar id: $jarId")
    basicRequest
      .delete(flinkUrl.addPath("jars", jarId))
      .send(backend)
      .flatMap(handleUnitResponse("delete jar"))
      .recoverWith(recoverWithMessage("delete jar"))
  }

  def findJobsByName(
      jobName: String
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[JobOverview]]] = {
    logger.trace(s"Checking fetching scenario $jobName state")
    basicRequest
      .readTimeout(config.scenarioStateRequestTimeout)
      .get(flinkUrl.addPath("jobs", "overview"))
      .response(asJson[JobsResponse])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .map { jobs =>
        jobs.jobs
          .filter(_.name == jobName)
          .sortBy(_.`last-modification`)
          .reverse
      }
      .map(WithDataFreshnessStatus.fresh)
      .recoverWith(recoverWithMessage("retrieve Flink jobs"))
  }

  def getJobConfig(jobId: String): Future[flinkRestModel.ExecutionConfig] = {
    basicRequest
      .get(flinkUrl.addPath("jobs", jobId, "config"))
      .response(asJson[JobConfig])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .map(_.`execution-config`)
  }

  // FIXME: get rid of sleep, refactor?
  def waitForSavepoint(
      jobId: ExternalDeploymentId,
      savepointId: String,
      timeoutLeft: Long = config.jobManagerTimeout.toMillis
  ): Future[SavepointResult] = {
    val start = System.currentTimeMillis()
    if (timeoutLeft <= 0) {
      return Future.failed(new Exception(s"Failed to complete savepoint in time for $jobId and trigger $savepointId"))
    }
    basicRequest
      .get(flinkUrl.addPath("jobs", jobId.value, "savepoints", savepointId))
      .response(asJson[GetSavepointStatusResponse])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .flatMap { resp =>
        logger.debug(s"Waiting for savepoint $savepointId of $jobId, got response: $resp")
        if (resp.isCompletedSuccessfully) {
          // getOrElse is not really needed since isCompletedSuccessfully returns true only if it's defined
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

  def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = {
    basicRequest
      .patch(flinkUrl.addPath("jobs", deploymentId.value))
      .send(backend)
      .flatMap(handleUnitResponse("cancel scenario"))
      .recoverWith(recoverWithMessage("cancel scenario"))

  }

  def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = {
    val savepointRequest = basicRequest
      .post(flinkUrl.addPath("jobs", deploymentId.value, "savepoints"))
      .body(SavepointTriggerRequest(`target-directory` = savepointDir, `cancel-job` = false))
    processSavepointRequest(deploymentId, savepointRequest, "make savepoint")
  }

  def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = {
    // because of https://issues.apache.org/jira/browse/FLINK-28758 we can't use '/stop' endpoint,
    // so jobs ends up in CANCELED state, not FINISHED - we should switch back when we get rid of old Kafka source
    val stopRequest = basicRequest
      .post(flinkUrl.addPath("jobs", deploymentId.value, "savepoints"))
      .body(SavepointTriggerRequest(`target-directory` = savepointDir, `cancel-job` = true))
    processSavepointRequest(deploymentId, stopRequest, "stop scenario")
  }

  private def processSavepointRequest(
      deploymentId: ExternalDeploymentId,
      request: RequestT[Identity, Either[String, String], Any],
      action: String
  ): Future[SavepointResult] = {
    request
      .response(asJson[SavepointTriggerResponse])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .flatMap { response =>
        waitForSavepoint(deploymentId, response.`request-id`)
      }
      .recoverWith(recoverWithMessage(action))
  }

  private val timeoutExtractor = DeeplyCheckingExceptionExtractor.forClass[TimeoutException]

  def runProgram(
      jarFile: File,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] = {
    val program =
      DeployProcessRequest(
        entryClass = mainClass,
        savepointPath = savepointPath,
        programArgs = FlinkArgsEncodeHack.prepareProgramArgs(args).mkString(" ")
      )
    uploadJarFileIfNotExists(jarFile).flatMap { flinkJarFile =>
      basicRequest
        .post(flinkUrl.addPath("jars", flinkJarFile.id, "run"))
        .body(program)
        .response(asJson[RunResponse])
        .send(backend)
        .flatMap(SttpJson.failureToFuture)
        .map(ret => Some(ExternalDeploymentId(ret.jobid)))
        .recover({
          // sometimes deploying takes too long, which causes TimeoutException while waiting for deploy response
          // workaround for now, not the best solution though
          // TODO: we should change logic of DeploymentService to mark process deployed for *some* exceptions (like Timeout here)
          case timeoutExtractor(e) =>
            logger.warn(
              "TimeoutException occurred while waiting for deploy result. Recovering with Future.successful...",
              e
            )
            None
        })
        .recoverWith(recoverWithMessage("deploy scenario"))
    }
  }

  def getClusterOverview: Future[ClusterOverview] = {
    basicRequest
      .get(flinkUrl.addPath("overview"))
      .response(asJson[ClusterOverview])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
  }

  def getJobManagerConfig: Future[Configuration] = {
    basicRequest
      .get(flinkUrl.addPath("jobmanager", "config"))
      .response(asJson[List[KeyValueEntry]])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .map(list => configurationFromMap(list.map(e => e.key -> e.value).toMap))
  }

  // we don't use Configuration.fromMap for Flink 1.11 compatibility
  private def configurationFromMap(values: Map[String, String]) = {
    val configuration = new Configuration();
    values.foreach { case (k, v) =>
      configuration.setString(k, v)
    }
    configuration
  }

}

object HttpFlinkClient {

  def createUnsafe(
      config: FlinkConfig
  )(implicit backend: SttpBackend[Future, Any], ec: ExecutionContext): HttpFlinkClient = {
    create(config).valueOr(err =>
      throw new IllegalArgumentException(err.toList.mkString("Cannot create HttpFlinkClient: ", ", ", ""))
    )
  }

  def create(
      config: FlinkConfig
  )(implicit backend: SttpBackend[Future, Any], ec: ExecutionContext): ValidatedNel[String, HttpFlinkClient] = {
    config.restUrl.map(valid).getOrElse(invalid("Invalid configuration: missing restUrl")).andThen { restUrl =>
      Try(uri"$restUrl")
        .map(valid)
        .getOrElse(invalid(s"Invalid configuration: restUrl is not a valid url [$restUrl]"))
        .map { parsedRestUrl =>
          new HttpFlinkClient(config, parsedRestUrl)
        }
    }
  }.toValidatedNel

}

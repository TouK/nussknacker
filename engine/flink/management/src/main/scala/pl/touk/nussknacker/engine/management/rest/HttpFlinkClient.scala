package pl.touk.nussknacker.engine.management.rest

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobID
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, SavepointResult, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel._
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.engine.sttp.SttpJson.asOptionalJson
import pl.touk.nussknacker.engine.util.exception.DeeplyCheckingExceptionExtractor
import sttp.client3._
import sttp.client3.circe._
import sttp.model.Uri

import java.io.File
import java.net.URI
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class HttpFlinkClient(restUrl: URI, scenarioStateRequestTimeout: FiniteDuration, jobManagerTimeout: FiniteDuration)(
    implicit backend: SttpBackend[Future, Any],
    ec: ExecutionContext
) extends FlinkClient
    with LazyLogging {

  private val flinkUrl = Uri(restUrl)

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

  override def getJobsOverviews()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[JobOverview]]] = {
    logger.trace(s"Fetching jobs overview")
    basicRequest
      .readTimeout(scenarioStateRequestTimeout)
      .get(flinkUrl.addPath("jobs", "overview"))
      .response(asJson[JobsResponse])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .map { jobs =>
        jobs.jobs
          .sortBy(_.`last-modification`)
          .reverse
      }
      .map { jobs =>
        logger.trace("Fetched jobs: " + jobs)
        jobs
      }
      .map(WithDataFreshnessStatus.fresh)
      .recoverWith(recoverWithMessage("retrieve Flink jobs"))
  }

  override def getJobDetails(jobId: JobID): Future[Option[JobDetails]] = {
    basicRequest
      .get(flinkUrl.addPath("jobs", jobId.toHexString))
      .response(asOptionalJson[JobDetails])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .recoverWith(recoverWithMessage("retrieve Flink job details"))
  }

  override def getJobConfig(jobId: JobID): Future[flinkRestModel.ExecutionConfig] = {
    basicRequest
      .get(flinkUrl.addPath("jobs", jobId.toHexString, "config"))
      .response(asJson[JobConfig])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .map(_.`execution-config`)
  }

  // FIXME: get rid of sleep, refactor?
  private def waitForSavepoint(
      jobId: JobID,
      savepointId: String,
      timeoutLeft: Long = jobManagerTimeout.toMillis
  ): Future[SavepointResult] = {
    val start = System.currentTimeMillis()
    if (timeoutLeft <= 0) {
      return Future.failed(new Exception(s"Failed to complete savepoint in time for $jobId and trigger $savepointId"))
    }
    basicRequest
      .get(flinkUrl.addPath("jobs", jobId.toHexString, "savepoints", savepointId))
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

  override def cancel(jobId: JobID): Future[Unit] = {
    basicRequest
      .patch(flinkUrl.addPath("jobs", jobId.toHexString))
      .send(backend)
      .flatMap(handleUnitResponse("cancel scenario"))
      .recoverWith(recoverWithMessage("cancel scenario"))

  }

  override def makeSavepoint(
      jobId: JobID,
      savepointDir: Option[String]
  ): Future[SavepointResult] = {
    val savepointRequest = basicRequest
      .post(flinkUrl.addPath("jobs", jobId.toHexString, "savepoints"))
      .body(SavepointTriggerRequest(`target-directory` = savepointDir, `cancel-job` = false))
    processSavepointRequest(jobId, savepointRequest, "make savepoint")
  }

  override def stop(jobId: JobID, savepointDir: Option[String]): Future[SavepointResult] = {
    // because of https://issues.apache.org/jira/browse/FLINK-28758 we can't use '/stop' endpoint,
    // so jobs ends up in CANCELED state, not FINISHED - we should switch back when we get rid of old Kafka source
    val stopRequest = basicRequest
      .post(flinkUrl.addPath("jobs", jobId.toHexString, "savepoints"))
      .body(SavepointTriggerRequest(`target-directory` = savepointDir, `cancel-job` = true))
    processSavepointRequest(jobId, stopRequest, "stop scenario")
  }

  private def processSavepointRequest(
      jobId: JobID,
      request: RequestT[Identity, Either[String, String], Any],
      action: String
  ): Future[SavepointResult] = {
    request
      .response(asJson[SavepointTriggerResponse])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
      .flatMap { response =>
        waitForSavepoint(jobId, response.`request-id`)
      }
      .recoverWith(recoverWithMessage(action))
  }

  private val timeoutExtractor = DeeplyCheckingExceptionExtractor.forClass[TimeoutException]

  override def runProgram(
      jarFile: File,
      mainClass: String,
      args: List[String],
      savepointPath: Option[String],
      jobId: Option[JobID]
  ): Future[Option[JobID]] = {
    val program =
      DeployProcessRequest(
        entryClass = mainClass,
        savepointPath = savepointPath,
        programArgsList = args,
        jobId = jobId
      )
    uploadJarFileIfNotExists(jarFile).flatMap { flinkJarFile =>
      basicRequest
        .post(flinkUrl.addPath("jars", flinkJarFile.id, "run"))
        .body(program)
        .response(asJson[RunResponse])
        .send(backend)
        .flatMap(SttpJson.failureToFuture)
        .map(ret => Some(ret.jobid))
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

  override def getClusterOverview: Future[ClusterOverview] = {
    basicRequest
      .get(flinkUrl.addPath("overview"))
      .response(asJson[ClusterOverview])
      .send(backend)
      .flatMap(SttpJson.failureToFuture)
  }

  override def getJobManagerConfig: Future[Configuration] = {
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

case class ParsedHttpFlinkClientConfig(
    restUrl: URI,
    scenarioStateRequestTimeout: FiniteDuration,
    jobManagerTimeout: FiniteDuration,
    scenarioStateCacheTTL: Option[FiniteDuration],
    jobConfigsCacheSize: Int
)

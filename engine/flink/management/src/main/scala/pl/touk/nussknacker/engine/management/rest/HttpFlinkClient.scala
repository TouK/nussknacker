package pl.touk.nussknacker.engine.management.rest

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, SavepointResult}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel._
import pl.touk.nussknacker.engine.management.{FlinkArgsEncodeHack, FlinkConfig}
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.engine.util.exception.DeeplyCheckingExceptionExtractor
import sttp.client.circe._
import sttp.client.{NothingT, SttpBackend, _}

import java.io.File
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

class HttpFlinkClient(config: FlinkConfig)(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext) extends FlinkClient with LazyLogging {

  private val flinkUrl = uri"${config.restUrl}"

  def uploadJarFileIfNotExists(jarFile: File): Future[JarFile] = {
    checkThatJarWithNameExists(jarFile.getName).flatMap {
      case Some(file) => Future.successful(file)
      case None => uploadJar(jarFile).map(id => JarFile(id, jarFile.getName))
    }
  }

  def checkThatJarWithNameExists(jarName: String): Future[Option[JarFile]] = {
    basicRequest
      .get(flinkUrl.path("jars"))
      .response(asJson[JarsResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .map(_.files.toList.flatten.find(_.name == jarName))
  }

  def uploadJar(jarFile: File): Future[String] = {
    logger.debug(s"Uploading new jar: ${jarFile.getAbsolutePath}")

    basicRequest
      .post(flinkUrl.path("jars", "upload"))
      .multipartBody( multipartFile("jarfile", jarFile).contentType("application/x-java-archive"))
      .response(asJson[UploadJarResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .map { file =>
        logger.info(s"Uploaded jar to $file")
        new File(file.filename).getName
    }
  }

  override def deleteJarIfExists(jarFileName: String): Future[Unit] = {
    checkThatJarWithNameExists(jarFileName).flatMap {
      case Some(file) =>
        deleteJar(file.id).recover {
          case ex =>
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
      .delete(flinkUrl.path("jars", jarId))
      .send()
      .flatMap(handleUnitResponse)
  }


  def findJobsByName(jobName: String): Future[List[JobOverview]] = {
    basicRequest
      .get(flinkUrl.path("jobs", "overview"))
      .response(asJson[JobsResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .map { jobs =>
        jobs.jobs
          .filter(_.name == jobName)
          .sortBy(_.`last-modification`)
          .reverse
      }
  }

  def getJobConfig(jobId: String): Future[flinkRestModel.ExecutionConfig] = {
    basicRequest
      .get(flinkUrl.path("jobs", jobId, "config"))
      .response(asJson[JobConfig])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .map(_.`execution-config`)
  }

  //FIXME: get rid of sleep, refactor?
  def waitForSavepoint(jobId: DeploymentId, savepointId: String, timeoutLeft: Long = config.jobManagerTimeout.toMillis): Future[SavepointResult] = {
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

  def cancel(deploymentId: DeploymentId): Future[Unit] = {
    basicRequest
      .patch(flinkUrl.path("jobs", deploymentId.value))
      .send()
      .flatMap(handleUnitResponse)
  }

  def makeSavepoint(deploymentId: DeploymentId, savepointDir: Option[String]): Future[SavepointResult] = {
    val savepointRequest = basicRequest
      .post(flinkUrl.path("jobs", deploymentId.value, "savepoints"))
      .body(SavepointTriggerRequest(`target-directory` = savepointDir, `cancel-job` = false))
    processSavepointRequest(deploymentId, savepointRequest)
  }

  def stop(deploymentId: DeploymentId, savepointDir: Option[String]): Future[SavepointResult] = {
    val stopRequest = basicRequest
      .post(flinkUrl.path("jobs", deploymentId.value, "stop"))
      .body(StopRequest(targetDirectory = savepointDir, drain = false))
    processSavepointRequest(deploymentId, stopRequest)
  }

  private def processSavepointRequest(deploymentId: DeploymentId, request: RequestT[Identity, Either[String, String], Nothing]): Future[SavepointResult] = {
    request
      .response(asJson[SavepointTriggerResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .flatMap { response =>
        waitForSavepoint(deploymentId, response.`request-id`)
      }
  }

  private val timeoutExtractor = DeeplyCheckingExceptionExtractor.forClass[TimeoutException]

  def runProgram(jarFile: File,
                 mainClass: String,
                 args: List[String],
                 savepointPath: Option[String]): Future[Unit] = {
    val program =
      DeployProcessRequest(
        entryClass = mainClass,
        savepointPath = savepointPath,
        programArgs = FlinkArgsEncodeHack.prepareProgramArgs(args).mkString(" "))
    uploadJarFileIfNotExists(jarFile).flatMap { flinkJarFile =>
      basicRequest
        .post(flinkUrl.path("jars", flinkJarFile.id, "run"))
        .body(program)
        .send()
        .flatMap(handleUnitResponse)
        .recover({
          //sometimes deploying takes too long, which causes TimeoutException while waiting for deploy response
          //workaround for now, not the best solution though
          //TODO: we should change logic of ManagementActor to mark process deployed for *some* exceptions (like Timeout here)
          case timeoutExtractor(e) =>
            logger.warn("TimeoutException occurred while waiting for deploy result. Recovering with Future.successful...", e)
            Future.successful(Unit)
        })
    }

  }

  private def handleUnitResponse(response: Response[Either[String, String]]): Future[Unit] = (response.code, response.body) match {
    case (code, Right(_)) if code.isSuccess => Future.successful(())
    case (code, Left(error)) => Future.failed(new RuntimeException(s"Request failed: $error, code: $code"))
  }

}

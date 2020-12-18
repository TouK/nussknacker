package pl.touk.nussknacker.engine.management.periodic

import java.io.File
import java.util.concurrent.TimeoutException

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.management.periodic.PeriodicFlinkRestModel.{DeployProcessRequest, JarsResponse}
import pl.touk.nussknacker.engine.management.flinkRestModel.UploadJarResponse
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.engine.util.exception.DeeplyCheckingExceptionExtractor
import sttp.client.{NothingT, Response, SttpBackend, basicRequest, multipartFile}
import sttp.model.Uri

import scala.concurrent.Future

private[periodic] trait FlinkClient {
  def uploadJarFileIfNotExists(jarFile: File): Future[String]
  def runJar(jarId: String, program: DeployProcessRequest): Future[Unit]
  def deleteJarIfExists(jarFileName: String): Future[Unit]
}

private[periodic] class HttpFlinkClient(flinkUrl: Uri)
                                       (implicit backend: SttpBackend[Future, Nothing, NothingT])
  extends FlinkClient with LazyLogging {

  import sttp.client.circe._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val timeoutExtractor = DeeplyCheckingExceptionExtractor.forClass[TimeoutException]

  override def uploadJarFileIfNotExists(jarFile: File): Future[String] = {
    findUploadedJar(jarFile.getName)
      .recoverWith { case _ => uploadJarFile(jarFile) }
  }

  private def findUploadedJar(jarName: String): Future[String] = {
    logger.info(s"Find uploaded jar: $jarName")
    basicRequest
      .get(flinkUrl.path("jars"))
      .response(asJson[JarsResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .flatMap { jars =>
        val maybeUploadedJar = jars.files.toList.flatten.find(_.name == jarName)
        maybeUploadedJar match {
          case Some(uploadedJar) => Future.successful(uploadedJar.id)
          case None => Future.failed(new PeriodicProcessException(s"Jar with name '$jarName' does not exist"))
        }
      }
  }

  private def uploadJarFile(jarFile: File): Future[String] = {
    logger.info(s"Uploading jar: $jarFile")
    basicRequest
      .post(flinkUrl.path("jars", "upload"))
      .multipartBody(multipartFile("jarfile", jarFile).contentType("application/x-java-archive"))
      .response(asJson[UploadJarResponse])
      .send()
      .flatMap(SttpJson.failureToFuture)
      .map { file =>
        logger.info(s"Uploaded jar to $file")
        new File(file.filename).getName
      }
  }

  override def runJar(jarId: String, program: DeployProcessRequest): Future[Unit] = {
    logger.info(s"Run jar: $jarId")
    basicRequest
      .post(flinkUrl.path("jars", jarId, "run"))
      .body(program)
      .send()
      .flatMap(handleUnitResponse)
      .recover({
        case timeoutExtractor(e) =>
          logger.warn("TimeoutException occurred while waiting for deploy result. Recovering with Future.successful...", e)
          Future.successful(Unit)
      })
  }

  override def deleteJarIfExists(jarFileName: String): Future[Unit] = {
    findUploadedJar(jarFileName)
      .flatMap(deleteJar)
      .recover { case ex =>
        logger.warn(s"Could not delete jar $jarFileName", ex)
      }
  }

  private def deleteJar(jarId: String): Future[Unit] = {
    logger.info(s"Delete jar id: $jarId")
    basicRequest
      .delete(flinkUrl.path("jars", jarId))
      .send()
      .flatMap(handleUnitResponse)
  }

  private def handleUnitResponse(response: Response[Either[String, String]]): Future[Unit] = response.body match {
    case Right(_) => Future.successful(())
    case Left(error) => Future.failed(new RuntimeException(s"Request failed: $error, code: ${response.code}"))
  }
}

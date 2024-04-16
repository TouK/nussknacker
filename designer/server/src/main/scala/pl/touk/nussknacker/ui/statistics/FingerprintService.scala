package pl.touk.nussknacker.ui.statistics

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.statistics.repository.FingerprintRepository
import slick.dbio.DBIO

import java.io.File
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

class FingerprintService(dbioRunner: DBIOActionRunner, fingerprintRepository: FingerprintRepository[DB])(
    implicit ec: ExecutionContext
) extends LazyLogging {

  def fingerprint(
      config: UsageStatisticsReportsConfig,
      fingerprintFileName: FileName
  ): Future[Either[CannotGenerateStatisticsError, Fingerprint]] = {
    // We filter out blank fingerprint and source because when smb uses docker-compose, and forwards env variables eg. USAGE_REPORTS_FINGERPRINT
    // from system and the variable doesn't exist, there is no way to skip variable - it can be only set to empty
    config.fingerprint.filterNot(_.isBlank) match {
      case Some(fingerprintFromConfig) => Future.successful(Right(new Fingerprint(fingerprintFromConfig)))
      case None                        => fetchOrGenerate(fingerprintFileName)
    }
  }

  private def fetchOrGenerate(
      fingerprintFileName: FileName
  ): Future[Either[CannotGenerateStatisticsError, Fingerprint]] = {
    val dbResult = for {
      dbFingerprint <- fingerprintRepository.read()
      result <- dbFingerprint match {
        case Some(dbValue) => DBIO.successful(new Fingerprint(dbValue))
        case None => {
          val generated = readFingerprintFromFile(fingerprintFileName).getOrElse(randomFingerprint)
          logger.info(s"Generated fingerprint $generated")
          fingerprintRepository
            .write(generated)
            .map(_ => new Fingerprint(generated))
        }
      }
    } yield result
    dbioRunner.safeRunInTransaction(dbResult) { ex =>
      logger.warn("Exception occurred during database access", ex)
      CannotGenerateStatisticsError
    }
  }

  // TODO: The code below is added to ensure compatibility with older NU versions and should be removed in future release of NU 1.20.
  private def readFingerprintFromFile(fingerprintFileName: FileName): Option[String] =
    Try(FileUtils.readFileToString(fingerprintFile(fingerprintFileName), StandardCharsets.UTF_8)) match {
      case Failure(_)     => None
      case Success(value) => Some(value.trim)
    }

  private def fingerprintFile(fingerprintFileName: FileName): File =
    new File(
      Try(Option(System.getProperty("java.io.tmpdir"))).toOption.flatten.getOrElse("/tmp"),
      fingerprintFileName.value
    )

  private def randomFingerprint = s"gen-${Random.alphanumeric.take(10).mkString}"
}

package pl.touk.nussknacker.ui.statistics

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.db.DBIOActionExtensions
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.statistics.repository.FingerprintRepository

import java.io.File
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

class FingerprintService(fingerprintRepository: FingerprintRepository[DB], fingerprintFileName: FileName)(
    implicit ec: ExecutionContext,
    dbioRunner: DBIOActionRunner
) extends LazyLogging
    with DBIOActionExtensions {

  def this(
      fingerprintRepository: FingerprintRepository[DB]
  )(implicit ec: ExecutionContext, dbioRunner: DBIOActionRunner) = {
    this(fingerprintRepository, new FileName("nussknacker.fingerprint"))(ec, dbioRunner)
  }

  def fingerprint(config: UsageStatisticsReportsConfig): Future[Either[StatisticError, Fingerprint]] = {
    // We filter out blank fingerprint and source because when smb uses docker-compose, and forwards env variables eg. USAGE_REPORTS_FINGERPRINT
    // from system and the variable doesn't exist, there is no way to skip variable - it can be only set to empty
    config.fingerprint.filterNot(_.isBlank) match {
      case Some(fingerprintFromConfig) => Future.successful(Right(new Fingerprint(fingerprintFromConfig)))
      case None                        => fetchOrGenerate(fingerprintFileName)
    }
  }

  private def fetchOrGenerate(
      fingerprintFileName: FileName
  ): Future[Either[StatisticError, Fingerprint]] =
    for {
      dbFingerprint <- fingerprintRepository.read().run
      result <- dbFingerprint match {
        case Some(dbValue) => Future.successful(Right(dbValue))
        case None => {
          val generated = readFingerprintFromFile(fingerprintFileName).getOrElse(randomFingerprint)
          fingerprintRepository
            .readOrSave(generated)
            .map {
              case Left(_) =>
                logger.warn("Cannot persist fingerprint in DB")
                Left(CannotGenerateStatisticsError)
              case Right(fingerprint) =>
                logger.debug(s"Saved fingerprint ${fingerprint.value}")
                Right(fingerprint)
            }
            .runInTransaction
        }
      }
    } yield result

  // TODO: The code below is added to ensure compatibility with older NU versions and should be removed in future release of NU 1.20.
  private def readFingerprintFromFile(fingerprintFileName: FileName): Option[Fingerprint] =
    Try(FileUtils.readFileToString(fingerprintFile(fingerprintFileName), StandardCharsets.UTF_8)) match {
      case Failure(_)     => None
      case Success(value) => Some(new Fingerprint(value.trim))
    }

  private def fingerprintFile(fingerprintFileName: FileName): File =
    new File(
      Try(Option(System.getProperty("java.io.tmpdir"))).toOption.flatten.getOrElse("/tmp"),
      fingerprintFileName.value
    )

  private def randomFingerprint = new Fingerprint(s"gen-${Random.alphanumeric.take(10).mkString}")
}

package pl.touk.nussknacker.ui.statistics.repository

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.entity.{FingerprintEntityData, FingerprintEntityFactory}
import pl.touk.nussknacker.ui.statistics.Fingerprint
import pl.touk.nussknacker.ui.statistics.repository.Error.SaveError
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

trait FingerprintRepository[F[_]] {
  def read(): F[Option[Fingerprint]]
  def readOrSave(freshFingerprint: Fingerprint): F[Either[Error, Fingerprint]]
}

class FingerprintRepositoryImpl(protected val dbRef: DbRef)(implicit ec: ExecutionContext)
    extends FingerprintRepository[DB]
    with FingerprintEntityFactory {

  protected val profile: JdbcProfile = dbRef.profile
  import profile.api._

  override def read(): DB[Option[Fingerprint]] = fingerprintsTable
    .take(1)
    .map(_.value)
    .result
    .map(_.headOption.map(f => new Fingerprint(f)))

  override def readOrSave(freshFingerprint: Fingerprint): DB[Either[Error, Fingerprint]] =
    read().flatMap {
      case Some(existingFingerprint) => DBIO.successful(Right(existingFingerprint))
      case None =>
        fingerprintsTable
          .+=(FingerprintEntityData(freshFingerprint.value))
          .map {
            case 1 => Right(freshFingerprint)
            case _ => Left(SaveError)
          }
    }

}

sealed trait Error

object Error {
  case object SaveError extends Error
}

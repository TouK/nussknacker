package pl.touk.nussknacker.ui.statistics.repository

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.entity.{FingerprintEntityData, FingerprintEntityFactory}
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

trait FingerprintRepository[F[_]] {
  def read(): F[Option[String]]
  def write(fingerprint: String): F[_]
}

class FingerprintRepositoryImpl(protected val dbRef: DbRef)(implicit ec: ExecutionContext)
    extends FingerprintRepository[DB]
    with FingerprintEntityFactory {

  protected val profile: JdbcProfile = dbRef.profile
  import profile.api._

  override def read(): DB[Option[String]] = fingerprintsTable
    .take(1)
    .map(_.value)
    .result
    .map(_.headOption)

  override def write(fingerprint: String): DB[_] = fingerprintsTable
    .insertOrUpdate(FingerprintEntityData(fingerprint))
}

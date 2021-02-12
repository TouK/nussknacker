package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.DbConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.Future

import scala.language.higherKinds

trait TransactionSupport[F[_]] {
  def runInTransaction(actions: F[_]*): Future[Unit]
}

class DBTransaction(dbConfig: DbConfig) extends TransactionSupport[DB] {

  protected lazy val profile: JdbcProfile = dbConfig.driver
  protected lazy val api : profile.API = profile.api
  import api._

  override def runInTransaction(actions: DB[_]*): Future[Unit] =
    dbConfig.run(DBIO.seq[Effect.All](actions: _*).transactionally)
}

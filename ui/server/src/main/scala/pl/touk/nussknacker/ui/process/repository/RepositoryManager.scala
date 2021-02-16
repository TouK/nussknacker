package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.DbConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.Future

import scala.language.higherKinds

object RepositoryManager {

  def createDbRepositoryManager(dbConfig: DbConfig): RepositoryManager[DB] =
    new RepositoryManager[DB] {

      protected lazy val profile: JdbcProfile = dbConfig.driver
      protected lazy val api : profile.API = profile.api
      import api._

      override def runInTransaction(actions: DB[_]*): Future[Unit] =
        run(DBIO.seq[Effect.All](actions: _*))

      override def run[T](action: DB[T]): Future[T] =
        dbConfig.run(action.transactionally)
    }
}

trait RepositoryManager[F[_]] {
  def runInTransaction(actions: F[_]*): Future[Unit]
  def run[T](action: F[T]): Future[T]
}

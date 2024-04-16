package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.DbRef
import slick.jdbc.JdbcProfile

import java.sql.SQLException
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

class DBIOActionRunner(dbRef: DbRef) {

  protected lazy val profile: JdbcProfile = dbRef.profile
  protected lazy val api: profile.API     = profile.api
  import api._

  def runInTransaction[T](action: DB[T]): Future[T] =
    run(action.transactionally)

  def run[T](action: DB[T]): Future[T] =
    dbRef.db.run(action)

  def safeRunInTransaction[T, E](action: DB[T])(error: Throwable => E)(
      implicit ec: ExecutionContext
  ): Future[Either[E, T]] =
    safeRun(action.transactionally)(error)

  def safeRun[E, T](action: DB[T])(error: Throwable => E)(
      implicit ec: ExecutionContext
  ): Future[Either[E, T]] =
    run(action.map(t => Right(t)))
      .recover { case ex: SQLException =>
        Left(error(ex))
      }

}

object DBIOActionRunner {
  def apply(db: DbRef): DBIOActionRunner = new DBIOActionRunner(db)
}

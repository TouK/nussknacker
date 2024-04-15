package pl.touk.nussknacker.ui.process.repository

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.DbRef
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success}

class DBIOActionRunner(dbRef: DbRef) extends LazyLogging {

  protected lazy val profile: JdbcProfile = dbRef.profile
  protected lazy val api: profile.API     = profile.api
  import api._

  def runInTransaction[T](action: DB[T]): Future[T] =
    run(action.transactionally)

  def run[T](action: DB[T]): Future[T] =
    dbRef.db.run(action)

  def safeRunInTransaction[T, E](action: DB[T], error: Throwable => E)(
      implicit ec: ExecutionContext
  ): Future[Either[E, T]] =
    safeRun(action.transactionally, error)

  def safeRun[T, E](action: DB[T], error: Throwable => E)(
      implicit ec: ExecutionContext
  ): Future[Either[E, T]] =
    run(action).transformWith {
      case Failure(ex) => {
        logger.warn("Exception occurred during database access", ex)
        Future.successful(Left(error(ex)))
      }
      case Success(value) => Future.successful(Right(value))
    }

}

object DBIOActionRunner {
  def apply(db: DbRef): DBIOActionRunner = new DBIOActionRunner(db)
}

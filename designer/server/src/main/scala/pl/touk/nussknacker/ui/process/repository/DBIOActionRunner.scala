package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances._
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner.TransactionsRunAttemptsExceedException
import slick.jdbc.{JdbcProfile, TransactionIsolation}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

class DBIOActionRunner(dbRef: DbRef)(implicit ec: ExecutionContext) {

  protected lazy val profile: JdbcProfile = dbRef.profile
  protected lazy val api: profile.API     = profile.api
  import api._

  def runInTransaction[T](action: DB[T]): Future[T] =
    run(action.transactionally)

  def runInSerializableTransactionWithRetry[T](
      action: DB[T],
      maxRetries: Int = 5,
      delay: FiniteDuration = 10.millis
  ): Future[T] = {
    def doRun(): Future[Try[T]] = {
      val transactionAction = action.transactionally.withTransactionIsolation(TransactionIsolation.Serializable)
      run(transactionAction).map(Success(_)).recover {
        case ex: java.sql.SQLException if ex.getSQLState == "40001" => Failure(ex)
      }
    }
    retry
      .JitterBackoff(maxRetries, delay)
      .apply(doRun())
      .map(_.fold[T](ex => throw new TransactionsRunAttemptsExceedException(ex, maxRetries), identity))
  }

  def run[T](action: DB[T]): Future[T] =
    dbRef.db.run(action)

}

object DBIOActionRunner {

  def apply(db: DbRef)(implicit ec: ExecutionContext): DBIOActionRunner = new DBIOActionRunner(db)

  class TransactionsRunAttemptsExceedException(cause: Throwable, limit: Int)
      extends Exception(s"Transactions exceeded $limit attempts limit", cause)

}

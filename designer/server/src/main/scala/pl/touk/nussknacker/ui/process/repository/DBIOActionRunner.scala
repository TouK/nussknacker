package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances._
import pl.touk.nussknacker.ui.db.{DbRef, SqlStates}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner.TransactionsRunAttemptsExceedException
import slick.jdbc.{JdbcProfile, TransactionIsolation}

import java.sql.SQLException
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
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
      maxRetries: Int = 10,
      initialDelay: FiniteDuration = 10.millis
  ): Future[T] = {
    val transactionAction = action.transactionally.withTransactionIsolation(TransactionIsolation.Serializable)
    def doRun(): Future[Try[T]] = {
      run(transactionAction).map(Success(_)).recover {
        case ex: SQLException if ex.getSQLState == SqlStates.SerializationFailure => Failure(ex)
      }
    }
    retry
      .JitterBackoff(maxRetries, initialDelay)
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

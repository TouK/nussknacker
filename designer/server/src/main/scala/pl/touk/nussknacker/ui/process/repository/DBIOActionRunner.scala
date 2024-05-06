package pl.touk.nussknacker.ui.process.repository

import cats.data.EitherT
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner.TransactionRollbackException
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.higherKinds

class DBIOActionRunner(dbRef: DbRef) {

  protected lazy val profile: JdbcProfile = dbRef.profile
  protected lazy val api: profile.API     = profile.api
  import api._

  def runInTransaction[T](action: DB[T]): Future[T] =
    run(action.transactionally)

  def run[T](action: DB[T]): Future[T] =
    dbRef.db.run(action)

  def runInTransactionE[Error, T](action: DB[Either[Error, T]]): Future[Either[Error, T]] =
    runInTransaction(action.map(_.fold(err => throw TransactionRollbackException(err), Right(_))))
      .recover { case TransactionRollbackException(error: Error @unchecked) =>
        Left(error)
      }

}

object DBIOActionRunner {

  def apply(db: DbRef): DBIOActionRunner = new DBIOActionRunner(db)

  private final case class TransactionRollbackException[Error](error: Error)
      extends Exception(s"Business error occurred: $error")

}

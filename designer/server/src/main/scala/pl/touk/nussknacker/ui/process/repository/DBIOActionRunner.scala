package pl.touk.nussknacker.ui.process.repository

import cats.data.EitherT
import cats.~>
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.ui.db.DbRef
import slick.jdbc.JdbcProfile

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

  def runInTransactionE[Error, T](action: EitherT[DB, Error, T]): EitherT[Future, Error, T] =
    action.mapK(new ~>[DB, Future] {
      override def apply[A](fa: DB[A]): Future[A] = runInTransaction(fa)
    })

}

object DBIOActionRunner {
  def apply(db: DbRef): DBIOActionRunner = new DBIOActionRunner(db)
}

package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.DbRef
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

trait Repository[F[_]] {

  def run[R]: DB[R] => F[R]

  protected val dbRef: DbRef

  // this has to be val, not def to have *stable* scala identifiers - we want to be able to do import api._
  protected lazy val profile: JdbcProfile = dbRef.profile

  protected lazy val api: profile.API = profile.api

}

trait DbioRepository extends Repository[DB] {

  override def run[R]: (DB[R]) => DB[R] = identity

}

trait BasicRepository extends Repository[Future] {

  import api._

  protected implicit def ec: ExecutionContext

  private val dbioRunner = DBIOActionRunner(dbRef)

  override def run[R]: (DB[R]) => Future[R] = a => dbioRunner.run(a.transactionally)

}

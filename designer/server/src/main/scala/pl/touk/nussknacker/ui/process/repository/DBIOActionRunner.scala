package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.DbConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.language.higherKinds

class DBIOActionRunner(dbConfig: DbConfig) {

  protected lazy val profile: JdbcProfile = dbConfig.profile
  protected lazy val api : profile.API = profile.api
  import api._

  def runInTransaction[T](action: DB[T]): Future[T] =
    run(action.transactionally)

  def run[T](action: DB[T]): Future[T] =
    dbConfig.db.run(action)
}

object DBIOActionRunner {
  def apply(dbConfig: DbConfig): DBIOActionRunner = new DBIOActionRunner(dbConfig)
}

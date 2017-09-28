package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.DbConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.language.higherKinds

trait Repository [F[_]] {

  def run[R]: DB[R] => F[R]

  val dbConfig: DbConfig

  //this has to be val, not def to have *stable* scala identifiers - we want to be able to do import api._ 
  protected lazy val driver: JdbcProfile = dbConfig.driver

  protected lazy val api : driver.API = driver.api

}

trait BasicRepository extends Repository[Future] {

  import api._

  override def run[R]: (DB[R]) => Future[R] = a => dbConfig.run(a.transactionally)

}

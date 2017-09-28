package pl.touk.nussknacker.ui.db

import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.Future

case class DbConfig(db: JdbcBackend.Database, driver: JdbcProfile) {
  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)
}
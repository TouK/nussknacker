package pl.touk.nussknacker.ui.customhttpservice.services

import slick.dbio.{DBIOAction, Effect, NoStream}

import scala.concurrent.Future

trait DatabaseRunner {
  type DB[A] = DBIOAction[A, NoStream, Effect.All]

  def runInTransaction[T](action: DB[T]): Future[T]
  def run[T](action: DB[T]): Future[T]
}

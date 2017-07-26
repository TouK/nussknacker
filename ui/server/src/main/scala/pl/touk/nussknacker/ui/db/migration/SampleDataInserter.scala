package pl.touk.nussknacker.ui.db.migration

import db.migration.DefaultJdbcProfile
import pl.touk.nussknacker.ui.db.EspTables
import slick.jdbc.JdbcBackend

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

object SampleDataInserter {

  import DefaultJdbcProfile.profile.api._
  implicit val ec = scala.concurrent.ExecutionContext.global
  def insert(db: JdbcBackend.DatabaseDef) = {
    // There is some slick issue with uppsert https://github.com/slick/slick/issues/966
    // So we do some quick and dirty uppserts, but it's ok because it's development data
    TryWithAwait(db.run(EspTables.environmentsTable += SampleData.environment))
    TryWithAwait(db.run(EspTables.processesTable += SampleData.process))
    TryWithAwait(db.run(EspTables.processVersionsTable += SampleData.processVersion))
    TryWithAwait(db.run(EspTables.deployedProcessesTable += SampleData.deployedProcess))
    TryWithAwait(db.run(EspTables.tagsTable ++= SampleData.tags))
    upsertComments(db)
  }

  def upsertComments(db: JdbcBackend.DatabaseDef): Try[Option[Int]] = {
    TryWithAwait(db.run(EspTables.commentsTable.filter(_.user === "TouK").delete))
    TryWithAwait(db.run(EspTables.commentsTable ++= SampleData.comments))
  }

  def TryWithAwait[A](a: Future[A]) = {
    Try { Await.result(a, 1 minute) }
  }
}

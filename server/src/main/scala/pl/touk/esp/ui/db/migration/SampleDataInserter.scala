package pl.touk.esp.ui.db.migration

import db.migration.DefaultJdbcProfile
import pl.touk.esp.ui.db.EspTables
import slick.jdbc.JdbcBackend

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

object SampleDataInserter {

  import DefaultJdbcProfile.profile.api._
  implicit val ec = scala.concurrent.ExecutionContext.global
  def insert(db: JdbcBackend.DatabaseDef) = {
    //Tu powinien byc upsert, ale slick niestety nie do konca umie https://github.com/slick/slick/issues/966
    //przez co inserty sie wywalaja jak ktos ma juz dzialajaca baze.
    //Robimy tak brzydko, bo nie chcemy pisac generycznego upserta do testowych insertow
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

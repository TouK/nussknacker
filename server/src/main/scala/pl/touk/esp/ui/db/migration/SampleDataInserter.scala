package pl.touk.esp.ui.db.migration

import db.migration.DefaultJdbcProfile
import pl.touk.esp.ui.db.EspTables
import slick.jdbc.JdbcBackend

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

object SampleDataInserter {

  def insert(db: JdbcBackend.DatabaseDef) = {
    import DefaultJdbcProfile.profile.api._
    implicit val ec = scala.concurrent.ExecutionContext.global
    //Tu powinien byc upsert, ale slick niestety nie do konca umie https://github.com/slick/slick/issues/966
    //przez co inserty sie wywalaja jak ktos ma juz dzialajaca baze.
    //Robimy tak brzydko, bo nie chcemy pisac generycznego upserta do testowych insertow
    TryWithAwait(db.run(EspTables.environmentsTable += SampleData.environment))
    TryWithAwait(db.run(EspTables.processesTable += SampleData.process))
    TryWithAwait(db.run(EspTables.processVersionsTable += SampleData.processVersion))
    TryWithAwait(db.run(EspTables.deployedProcessesTable += SampleData.deployedProcess))
    TryWithAwait(db.run(EspTables.tagsTable ++= SampleData.tags))
  }

  def TryWithAwait[A](a: Future[A]) = {
    Try { Await.result(a, 1 minute) }
  }
}

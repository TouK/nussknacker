package pl.touk.nussknacker.ui.initialization

import cats.instances.list._
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.db.{DbRef, EspTables}
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.db.entity.EnvironmentsEntityData
import pl.touk.nussknacker.ui.process.migrate.ProcessModelMigrator
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object Initialization {

  implicit val nussknackerUser: LoggedUser = NussknackerInternalUser.instance
  def init(
      migrations: ProcessingTypeDataProvider[ProcessMigrations, _],
      db: DbRef,
      fetchingRepository: DBFetchingProcessRepository[DB],
      environment: String
  )(implicit ec: ExecutionContext): Unit = {
    val processRepository = new DBProcessRepository(db, migrations.mapValues(_.version))

    val operations: List[InitialOperation] = List(
      new EnvironmentInsert(environment, db),
      new AutomaticMigration(migrations, processRepository, fetchingRepository)
    )

    runOperationsTransactionally(db, operations)
  }

  private def runOperationsTransactionally(db: DbRef, operations: List[InitialOperation])(
      implicit ec: ExecutionContext
  ): List[Unit] = {

    import db.profile.api._

    val result    = operations.map(_.runOperation).sequence[DB, Unit]
    val runFuture = DBIOActionRunner(db).run(result.transactionally)

    // TODO: make it more configurable...
    Await.result(runFuture, 10 minute)
  }
}

trait InitialOperation extends LazyLogging {

  def runOperation(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit]

}

class EnvironmentInsert(environmentName: String, dbRef: DbRef) extends InitialOperation {
  override def runOperation(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit] = {
    // `insertOrUpdate` in Slick v.3.2.0-M1 seems not to work
    import dbRef.profile.api._
    val espTables = new EspTables {
      override implicit val profile: JdbcProfile = dbRef.profile
    }
    val uppsertEnvironmentAction = for {
      alreadyExists <- espTables.environmentsTable.filter(_.name === environmentName).exists.result
      _ <-
        if (alreadyExists) {
          DBIO.successful(())
        } else {
          espTables.environmentsTable += EnvironmentsEntityData(environmentName)
        }
    } yield ()
    uppsertEnvironmentAction
  }
}

class AutomaticMigration(
    migrations: ProcessingTypeDataProvider[ProcessMigrations, _],
    processRepository: DBProcessRepository,
    fetchingProcessRepository: DBFetchingProcessRepository[DB]
) extends InitialOperation {

  private val migrator = new ProcessModelMigrator(migrations)

  def runOperation(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit] = {
    val results: DB[List[Unit]] = for {
      allToMigrate <- fetchingProcessRepository.fetchProcessesDetails[DisplayableProcess](
        FetchProcessesDetailsQuery.unarchived
      )
      migrated <- allToMigrate.map(migrateOne).sequence[DB, Unit]
    } yield migrated
    results.map(_ => ())
  }

  private def migrateOne(processDetails: ProcessDetails)(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit] = {
    // TODO: unsafe processId?
    migrator
      .migrateProcess(processDetails, skipEmptyMigrations = true)
      .map(_.toUpdateAction(ProcessId(processDetails.processId.value))) match {
      case Some(action) =>
        processRepository.updateProcess(action).flatMap {
          case Left(error) =>
            DBIOAction.failed(new RuntimeException(s"Failed to migrate ${processDetails.name}: $error"))
          case Right(_) => DBIOAction.successful(())
        }
      case None => DBIOAction.successful(())
    }
  }
}

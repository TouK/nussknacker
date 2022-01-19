package pl.touk.nussknacker.ui.initialization

import cats.instances.list._
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.db.entity.EnvironmentsEntityData
import pl.touk.nussknacker.ui.process.migrate.ProcessModelMigrator
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object Initialization {

  implicit val nussknackerUser: LoggedUser = NussknackerInternalUser
  def init(migrations: ProcessingTypeDataProvider[ProcessMigrations], db: DbConfig, environment: String)(implicit ec: ExecutionContext) : Unit = {
    val processRepository = new DBProcessRepository(db, migrations.mapValues(_.version))

    val transactionalFetchingRepository = new DBFetchingProcessRepository[DB](db) {
      override def run[R]: DB[R] => DB[R] = identity
    }

    val operations : List[InitialOperation] = List(
      new EnvironmentInsert(environment, db),
      new AutomaticMigration(migrations, processRepository, transactionalFetchingRepository)
    )

    runOperationsTransactionally(db, operations)
  }

  private def runOperationsTransactionally(db: DbConfig, operations: List[InitialOperation])(implicit ec: ExecutionContext): List[Unit] = {

    import db.driver.api._

    val result = operations.map(_.runOperation).sequence[DB, Unit]
    val runFuture = db.run(result.transactionally)

    //TODO: make it more configurable...
    Await.result(runFuture, 10 minute)
  }
}

trait InitialOperation extends LazyLogging {

  def runOperation(implicit ec: ExecutionContext, lu: LoggedUser) : DB[Unit]


}

class EnvironmentInsert(environmentName: String, dbConfig: DbConfig) extends InitialOperation {
  override def runOperation(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit] = {
    //`insertOrUpdate` in Slick v.3.2.0-M1 seems not to work
    import dbConfig.driver.api._
    val espTables = new EspTables {
      override implicit val profile: JdbcProfile = dbConfig.driver
    }
    val uppsertEnvironmentAction = for {
      alreadyExists <- espTables.environmentsTable.filter(_.name === environmentName).exists.result
      _ <- if (alreadyExists) {
        DBIO.successful(())
      } else {
        espTables.environmentsTable += EnvironmentsEntityData(environmentName)
      }
    } yield ()
    uppsertEnvironmentAction
  }
}

class AutomaticMigration(migrations: ProcessingTypeDataProvider[ProcessMigrations],
                         processRepository: DBProcessRepository,
                         fetchingProcessRepository: DBFetchingProcessRepository[DB]) extends InitialOperation {

  private val migrator = new ProcessModelMigrator(migrations)

  def runOperation(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit] = {
    val results : DB[List[Unit]] = for {
      processes <- fetchingProcessRepository.fetchProcessesDetails[DisplayableProcess]()
      subprocesses <- fetchingProcessRepository.fetchSubProcessesDetails[DisplayableProcess]()
      allToMigrate = processes ++ subprocesses
      migrated <- allToMigrate.map(migrateOne).sequence[DB, Unit]
    } yield migrated
    results.map(_ => ())
  }

  private def migrateOne(processDetails: ProcessDetails)(implicit ec: ExecutionContext, lu: LoggedUser) : DB[Unit] = {
    // todo: unsafe processId?
    migrator.migrateProcess(processDetails, skipEmptyMigrations = true).map(_.toUpdateAction(ProcessId(processDetails.processId.value))) match {
      case Some(action) => processRepository.updateProcess(action).flatMap {
        case Left(error) => DBIOAction.failed(new RuntimeException(s"Failed to migrate ${processDetails.name}: $error"))
        case Right(_) => DBIOAction.successful(())
      }
      case None => DBIOAction.successful(())
    }
  }
}


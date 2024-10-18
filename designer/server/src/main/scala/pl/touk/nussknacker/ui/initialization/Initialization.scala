package pl.touk.nussknacker.ui.initialization

import cats.instances.list._
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.ui.db.entity.EnvironmentsEntityData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.process.migrate.ProcessModelMigrator
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import java.time.Clock
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object Initialization {

  implicit val nussknackerUser: LoggedUser = NussknackerInternalUser.instance

  def init(
      migrations: ProcessingTypeDataProvider[ProcessMigrations, _],
      db: DbRef,
      clock: Clock,
      fetchingRepository: DBFetchingProcessRepository[DB],
      scenarioActivityRepository: ScenarioActivityRepository,
      scenarioLabelsRepository: ScenarioLabelsRepository,
      environment: String,
  )(implicit ec: ExecutionContext): Unit = {
    val processRepository =
      new DBProcessRepository(
        db,
        clock,
        scenarioActivityRepository,
        scenarioLabelsRepository,
        migrations.mapValues(_.version)
      )

    val operations: List[InitialOperation] = List(
      new EnvironmentInsert(environment, db),
      new AutomaticMigration(migrations.mapValues(new ProcessModelMigrator(_)), processRepository, fetchingRepository)
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
    val nuTables = new NuTables {
      override implicit val profile: JdbcProfile = dbRef.profile
    }
    val uppsertEnvironmentAction = for {
      alreadyExists <- nuTables.environmentsTable.filter(_.name === environmentName).exists.result
      _ <-
        if (alreadyExists) {
          DBIO.successful(())
        } else {
          nuTables.environmentsTable += EnvironmentsEntityData(environmentName)
        }
    } yield ()
    uppsertEnvironmentAction
  }

}

class AutomaticMigration(
    migrators: ProcessingTypeDataProvider[ProcessModelMigrator, _],
    processRepository: DBProcessRepository,
    fetchingProcessRepository: DBFetchingProcessRepository[DB]
) extends InitialOperation {

  def runOperation(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit] = {
    val results: DB[List[Unit]] = for {
      allToMigrate <- fetchingProcessRepository.fetchLatestProcessesDetails[ScenarioGraph](
        ScenarioQuery.unarchived
      )
      migrated <- allToMigrate.map(migrateOne).sequence[DB, Unit]
    } yield migrated
    results.map(_ => ())
  }

  private def migrateOne(
      processDetails: ScenarioWithDetailsEntity[ScenarioGraph]
  )(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit] = {
    DBIOAction
      .sequenceOption(for {
        migrator        <- migrators.forProcessingType(processDetails.processingType)
        migrationResult <- migrator.migrateProcess(processDetails, skipEmptyMigrations = true)
        automaticUpdateAction = migrationResult
          .toAutomaticProcessUpdateAction(
            processDetails.processId,
            processDetails.scenarioLabels.map(ScenarioLabel.apply)
          )
      } yield {
        processRepository.performAutomaticUpdate(automaticUpdateAction)
      })
      .map(_ => ())
  }

}

package pl.touk.nussknacker.ui.initialization

import cats.data.EitherT
import cats.instances.list._
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, ProcessDeploymentData}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.db.entity.EnvironmentsEntityData
import pl.touk.nussknacker.ui.process.migrate.ProcessModelMigrator
import pl.touk.nussknacker.ui.process.repository.WriteProcessRepository.UpdateProcessAction
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser, Permission}
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}


object Initialization {

  implicit val nussknackerUser: LoggedUser = NussknackerInternalUser
  def init(migrations: Map[ProcessingType, ProcessMigrations],
           db: DbConfig,
           environment: String,
           customProcesses: Option[Map[String, String]])(implicit ec: ExecutionContext) : Unit = {

    val transactionalRepository = new DbWriteProcessRepository[DB](db, migrations.mapValues(_.version)) {
      override def run[R]: DB[R] => DB[R] = identity
    }
    val transactionalFetchingRepository = new DBFetchingProcessRepository[DB](db) {
      override def run[R]: DB[R] => DB[R] = identity
    }

    val operations : List[InitialOperation] = List(
      new EnvironmentInsert(environment, db),
      new AutomaticMigration(migrations, transactionalRepository, transactionalFetchingRepository)
    ) ++ customProcesses.map(new TechnicalProcessUpdate(_, transactionalRepository, transactionalFetchingRepository))

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

//FIXME: this is pretty clunky - e.g. cannot define category/processingtype for technical type - it's hardcoded as streaming...
class TechnicalProcessUpdate(customProcesses: Map[String, String], repository: DbWriteProcessRepository[DB], fetchingProcessRepository: DBFetchingProcessRepository[DB])
  extends InitialOperation  {

  def runOperation(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit] = {
    val results: DB[List[Unit]] = customProcesses
      .map { case (processName, processClass) =>
        val deploymentData = CustomProcess(processClass)
        logger.info(s"Saving custom process $processName")
        saveOrUpdate(
          processName = ProcessName(processName),
          category = "Technical",
          deploymentData = deploymentData,
          processingType = "streaming",
          isSubprocess = false
        )
      }.toList.sequence[DB, Unit]
    results.map(_ => ())
  }

  private def saveOrUpdate(processName: ProcessName, category: String, deploymentData: ProcessDeploymentData,
                           processingType: ProcessingType, isSubprocess: Boolean)(implicit ec: ExecutionContext, lu: LoggedUser): DB[Unit] = {
    (for {
      processIdOpt <- EitherT.right[EspError](fetchingProcessRepository.fetchProcessId(processName))
      _ <- EitherT[DB, EspError, Unit] {
        processIdOpt match {
          case None =>
            repository.saveNewProcess(
              processName = processName,
              category = category,
              processDeploymentData = deploymentData,
              processingType = processingType,
              isSubprocess = isSubprocess
            ).map(_.right.map(_ => ()))
          case Some(processId) =>
            fetchingProcessRepository.fetchLatestProcessVersion[Unit](processId).flatMap {
              case Some(version) if version.user == Initialization.nussknackerUser.username =>
                repository.updateProcess(UpdateProcessAction(processId, deploymentData, "External update")).map(_.right.map(_ => ()))
              case latestVersion => logger.info(s"Process $processId not updated. DB version is: \n${latestVersion.flatMap(_.json).getOrElse("")}\n " +
                s" and version from file is: \n$deploymentData")
                DBIOAction.successful(Right(()))
            }.andThen {
              repository.updateCategory(processId, category)
            }
        }
      }
    } yield ()).value.flatMap {
      case Left(error) => DBIOAction.failed(new RuntimeException(s"Failed to migrate ${processName.value}: $error"))
      case Right(()) => DBIOAction.successful(())
    }
  }
}

class AutomaticMigration(migrations: Map[ProcessingType, ProcessMigrations],
                         repository: DbWriteProcessRepository[DB], fetchingProcessRepository: DBFetchingProcessRepository[DB]) extends InitialOperation {

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
    migrator.migrateProcess(processDetails).map(_.toUpdateAction(ProcessId(processDetails.id.toLong))) match {
      case Some(action) => repository.updateProcess(action).flatMap {
        case Left(error) => DBIOAction.failed(new RuntimeException(s"Failed to migrate ${processDetails.name}: $error"))
        case Right(_) => DBIOAction.successful(())
      }
      case None => DBIOAction.successful(())
    }
  }
}


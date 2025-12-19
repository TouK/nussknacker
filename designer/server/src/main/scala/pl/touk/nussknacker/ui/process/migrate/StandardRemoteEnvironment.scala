package pl.touk.nussknacker.ui.process.migrate

import cats.data.EitherT
import cats.implicits._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.model._
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.ui.{FatalError, NuDesignerError}
import pl.touk.nussknacker.ui.migrations.{MigrateScenarioData, MigrateScenarioDataV3, MigrationApiAdapterService}
import pl.touk.nussknacker.ui.migrations.MigrationService.MigrationApiAdapterError
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{ApiAdapterServiceError, ScenarioGraphComparator}
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator.Difference

import scala.collection.parallel.CollectionConverters._
import scala.collection.parallel.ExecutionContextTaskSupport
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final case class StandardRemoteEnvironmentConfig(
    batchSize: Int = 10,
    batchTimeout: FiniteDuration = 120 seconds
)

//TODO: extract interface to remote environment?
trait StandardRemoteEnvironment extends FailFastCirceSupport with RemoteEnvironment with LazyLogging {

  protected implicit val ec: ExecutionContext

  protected val localEnvironmentId: String

  protected val remoteEnvironmentId: String

  protected type FutureE[T] = EitherT[Future, NuDesignerError, T]

  private val migrationApiAdapterService: MigrationApiAdapterService = new MigrationApiAdapterService()

  def config: StandardRemoteEnvironmentConfig

  def testModelMigrations: TestModelMigrations

  override def environmentId: String = remoteEnvironmentId

  override def compare(
      localGraph: ScenarioGraph,
      remoteProcessName: ProcessName,
      remoteProcessVersion: Option[VersionId]
  ): Future[Either[NuDesignerError, Map[String, Difference]]] = {
    (for {
      process <- fetchProcessVersion(remoteProcessName, remoteProcessVersion)
      compared <- EitherT.rightT[Future, NuDesignerError](
        ScenarioGraphComparator.compare(localGraph, process.scenarioGraphUnsafe)
      )
    } yield compared).value
  }

  override def migrate(
      processingMode: ProcessingMode,
      engineSetupName: EngineSetupName,
      processCategory: String,
      scenarioLabels: List[String],
      scenarioGraph: ScenarioGraph,
      localScenarioVersionId: VersionId,
      processName: ProcessName,
      isFragment: Boolean
  )(implicit loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = {

    val result: EitherT[Future, NuDesignerError, Unit] = for {
      remoteScenarioDescriptionVersion <- fetchRemoteMigrationScenarioDescriptionVersion
      localScenarioDescriptionVersion = migrationApiAdapterService.getCurrentApiVersion
      migrateScenarioRequest: MigrateScenarioData =
        MigrateScenarioDataV3(
          localEnvironmentId,
          Some(localScenarioVersionId),
          processingMode,
          engineSetupName,
          processCategory,
          scenarioLabels,
          scenarioGraph,
          processName,
          isFragment
        )
      versionsDifference = localScenarioDescriptionVersion - remoteScenarioDescriptionVersion
      transformedMigrateScenarioRequestE =
        if (versionsDifference > 0)
          migrationApiAdapterService.adaptDown(migrateScenarioRequest, versionsDifference)
        else
          Right(migrateScenarioRequest)
      _ <- handleTransformedMigrateScenarioRequest(transformedMigrateScenarioRequestE)
    } yield ()

    result.value
  }

  private def handleTransformedMigrateScenarioRequest(
      transformedMigrateScenarioRequestE: Either[ApiAdapterServiceError, MigrateScenarioData]
  )(implicit ec: ExecutionContext, loggedUser: LoggedUser): EitherT[Future, NuDesignerError, Unit] = {
    transformedMigrateScenarioRequestE match {
      case Left(apiAdapterServiceError) =>
        logger.warn(s"Migration error: $apiAdapterServiceError")
        EitherT.leftT(MigrationApiAdapterError(apiAdapterServiceError))
      case Right(transformedMigrateScenarioRequest) =>
        migrateScenario(transformedMigrateScenarioRequest)
    }
  }

  // We need to be cautious when choosing maxParallelism of batchingExecutionContext as validation may call external systems and we don't want to overwhelm them with requests
  override def testMigration(
      processToInclude: ScenarioWithDetailsForMigrations => Boolean = _ => true,
      batchingExecutionContext: ExecutionContext
  )(implicit user: LoggedUser): Future[Either[NuDesignerError, List[TestMigrationResult]]] = {
    (for {
      allBasicProcesses <- fetchProcesses
      basicProcesses = allBasicProcesses.filterNot(_.isFragment).filter(processToInclude)
      basicFragments = allBasicProcesses.filter(_.isFragment).filter(processToInclude)
      processes <- fetchGroupByGroup(basicProcesses, batchingExecutionContext)
      fragments <- fetchGroupByGroup(basicFragments, batchingExecutionContext)
    } yield testModelMigrations.testMigrations(processes, fragments, batchingExecutionContext)).value
  }

  private def fetchGroupByGroup(
      basicProcesses: List[ScenarioWithDetailsForMigrations],
      batchingExecutionContext: ExecutionContext
  ): FutureE[List[ScenarioWithDetailsForMigrations]] = {
    val groupedBasicProcesses = basicProcesses
      .map(_.name)
      .grouped(config.batchSize)
      .toVector
    val parallelCollection = groupedBasicProcesses.par
    parallelCollection.tasksupport = new ExecutionContextTaskSupport(batchingExecutionContext)
    val fetchProcessDetailsOperation = parallelCollection.map(processesGroup => {
      Await.result(fetchProcessesDetails(processesGroup).value, config.batchTimeout)
    })
    EitherT {
      Future {
        for {
          scenariosWithDetails <- fetchProcessDetailsOperation.toList.sequence
        } yield scenariosWithDetails.flatten
      }
    }
  }

  protected def fetchRemoteMigrationScenarioDescriptionVersion: FutureE[Int]

  protected def migrateScenario(migrateScenarioData: MigrateScenarioData)(
      implicit loggedUser: LoggedUser
  ): FutureE[Unit]

  protected def fetchProcesses: FutureE[List[ScenarioWithDetailsForMigrations]]

  protected def fetchProcessVersion(
      name: ProcessName,
      remoteProcessVersion: Option[VersionId]
  ): FutureE[ScenarioWithDetailsForMigrations]

  protected def fetchProcessesDetails(names: List[ProcessName]): FutureE[List[ScenarioWithDetailsForMigrations]]

}

final case class RemoteEnvironmentCommunicationError(statusCode: StatusCode, message: String)
    extends FatalError(message)

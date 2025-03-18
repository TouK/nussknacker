package pl.touk.nussknacker.ui.process.migrate

import cats.data.EitherT
import cats.implicits._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.Materializer
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.ui.{FatalError, NuDesignerError}
import pl.touk.nussknacker.ui.migrations.{MigrateScenarioData, MigrateScenarioDataV3, MigrationApiAdapterService}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{ApiAdapterServiceError, OutOfRangeAdapterRequestError, ScenarioGraphComparator}
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator.Difference

import scala.collection.parallel.ExecutionContextTaskSupport
import scala.collection.parallel.immutable.ParVector
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final case class StandardRemoteEnvironmentConfig(
    batchSize: Int = 10,
    batchTimeout: FiniteDuration = 120 seconds
)

//TODO: extract interface to remote environment?
trait StandardRemoteEnvironment extends FailFastCirceSupport with RemoteEnvironment with LazyLogging {

  private type FutureE[T] = EitherT[Future, NuDesignerError, T]

  private val migrationApiAdapterService: MigrationApiAdapterService = new MigrationApiAdapterService()

  def config: StandardRemoteEnvironmentConfig

  def testModelMigrations: TestModelMigrations

  implicit def materializer: Materializer

  override def compare(
      localGraph: ScenarioGraph,
      remoteProcessName: ProcessName,
      remoteProcessVersion: Option[VersionId]
  )(
      implicit ec: ExecutionContext
  ): Future[Either[NuDesignerError, Map[String, Difference]]] = {
    (for {
      process <- EitherT(fetchProcessVersion(remoteProcessName, remoteProcessVersion))
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
  )(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = {

    val result: EitherT[Future, NuDesignerError, Unit] = for {
      remoteScenarioDescriptionVersion <- fetchRemoteMigrationScenarioDescriptionVersion
      localScenarioDescriptionVersion = migrationApiAdapterService.getCurrentApiVersion
      migrateScenarioRequest: MigrateScenarioData =
        MigrateScenarioDataV3(
          environmentId,
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
        EitherT.leftT(MigrationApiAdapterError(apiAdapterServiceError))
      case Right(transformedMigrateScenarioRequest) =>
        EitherT(
          migrateScenario(transformedMigrateScenarioRequest)
        )
    }
  }

  // We need to be cautious when choosing maxParallelism of batchingExecutionContext as validation may call external systems and we don't want to overwhelm them with requests
  override def testMigration(
      processToInclude: ScenarioWithDetailsForMigrations => Boolean = _ => true,
      batchingExecutionContext: ExecutionContext
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[NuDesignerError, List[TestMigrationResult]]] = {
    (for {
      allBasicProcesses <- EitherT(fetchProcesses)
      basicProcesses = allBasicProcesses.filterNot(_.isFragment).filter(processToInclude)
      basicFragments = allBasicProcesses.filter(_.isFragment).filter(processToInclude)
      processes <- fetchGroupByGroup(basicProcesses, batchingExecutionContext)
      fragments <- fetchGroupByGroup(basicFragments, batchingExecutionContext)
    } yield testModelMigrations.testMigrations(processes, fragments, batchingExecutionContext)).value
  }

  private def fetchGroupByGroup(
      basicProcesses: List[ScenarioWithDetailsForMigrations],
      batchingExecutionContext: ExecutionContext
  )(implicit ec: ExecutionContext): FutureE[List[ScenarioWithDetailsForMigrations]] = {
    val groupedBasicProcesses = basicProcesses
      .map(_.name)
      .grouped(config.batchSize)
      .toVector
    // We create ParVector manually instead of calling par for compatibility with Scala 2.12
    val parallelCollection = new ParVector(groupedBasicProcesses)
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

  override def processVersions(processName: ProcessName)(implicit ec: ExecutionContext): Future[List[ScenarioVersion]] =
    ???

  protected def fetchRemoteMigrationScenarioDescriptionVersion(
      implicit ec: ExecutionContext
  ): EitherT[Future, NuDesignerError, Int] = ???

  protected def migrateScenario(migrateScenarioData: MigrateScenarioData)(
      implicit ec: ExecutionContext,
      loggedUser: LoggedUser
  ): Future[Either[NuDesignerError, Unit]] = ???

  protected def fetchProcesses(
      implicit ec: ExecutionContext
  ): Future[Either[NuDesignerError, List[ScenarioWithDetailsForMigrations]]] = ???

  protected def fetchProcessVersion(name: ProcessName, remoteProcessVersion: Option[VersionId])(
      implicit ec: ExecutionContext
  ): Future[Either[NuDesignerError, ScenarioWithDetailsForMigrations]] = ???

  protected def fetchProcessesDetails(names: List[ProcessName])(
      implicit ec: ExecutionContext
  ): EitherT[Future, NuDesignerError, List[ScenarioWithDetailsForMigrations]] = ???

}

final case class MigrationApiAdapterError(apiAdapterError: ApiAdapterServiceError)
    extends FatalError(
      apiAdapterError match {
        case OutOfRangeAdapterRequestError(currentVersion, signedNoOfVersionsLeftToApply) =>
          signedNoOfVersionsLeftToApply match {
            case n if n >= 0 =>
              s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to $signedNoOfVersionsLeftToApply version(s) up"
            case _ =>
              s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to ${-signedNoOfVersionsLeftToApply} version(s) down"
          }
      }
    )

final case class RemoteEnvironmentCommunicationError(statusCode: StatusCode, message: String)
    extends FatalError(message)

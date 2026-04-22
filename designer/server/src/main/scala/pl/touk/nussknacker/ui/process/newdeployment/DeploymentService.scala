package pl.touk.nussknacker.ui.process.newdeployment

import cats.Applicative
import cats.data.{EitherT, NonEmptyList}
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.{ProcessVersion => RuntimeVersionData}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  DesignerWideComponentId,
  NodesDeploymentData
}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessingType, ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.{
  AdditionalModelConfigs,
  DeploymentData,
  DeploymentId => LegacyDeploymentId
}
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.engine.util.{AdditionalComponentConfigsForRuntimeExtractor, ExecutionContextWithIORuntime}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.limits.LimitsService
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError
import pl.touk.nussknacker.ui.process.ScenarioMetadata
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.{DeploymentEntityData, WithModifiedAt}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentService._
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ScenarioMetadataRepository}
import pl.touk.nussknacker.ui.process.version.ScenarioGraphVersionService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.sql.Timestamp
import java.time.Clock
import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.control.NonFatal

// TODO: This class is a new version of deployment.DeploymentService. The problem with the old one is that
//       it has assumption, that scenario can have only one deployment. This is reasonable for streaming
//       scenarios but not for batch scenarios which can have many invocation done concurrently.
//       We should extract the logic related with streaming scenarios lifecycle to some separated class
//       and use this class for deployment management for both streaming and batch cases
class DeploymentService(
    scenarioMetadataRepository: ScenarioMetadataRepository,
    scenarioGraphVersionService: ScenarioGraphVersionService,
    deploymentRepository: DeploymentRepository,
    dmDispatcher: DeploymentManagerDispatcher,
    dbioRunner: DBIOActionRunner,
    clock: Clock,
    additionalComponentConfigs: ProcessingTypeDataProvider[
      Map[DesignerWideComponentId, ComponentAdditionalConfig],
      _
    ],
    limitsService: LimitsService
)(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime)
    extends LazyLogging {

  import executionContextWithIORuntime.ioRuntime

  def getDeploymentStatus(
      id: DeploymentId
  )(implicit loggedUser: LoggedUser): Future[Either[GetDeploymentStatusError, WithModifiedAt[DeploymentStatus]]] =
    (for {
      deploymentWithScenarioMetadata <- getDeploymentById(id)
      _ <- checkPermission[Future, GetDeploymentStatusError](
        user = loggedUser,
        category = deploymentWithScenarioMetadata.scenarioMetadata.processCategory,
        permission = Permission.Read
      )
    } yield deploymentWithScenarioMetadata.deployment.statusWithModifiedAt).value

  def runDeployment(command: RunDeploymentCommand): Future[Either[RunDeploymentError, DeploymentForeignKeys]] =
    (for {
      scenarioMetadata <- getScenarioMetadata(command)
      _ <- checkPermission[Future, RunDeploymentError](
        user = command.user,
        category = scenarioMetadata.processCategory,
        permission = Permission.Deploy
      )
      scenarioGraphVersion <- getScenarioGraphVersion(scenarioMetadata, command.user)
      deploymentUpdateStrategy = DeploymentUpdateStrategy.DontReplaceDeployment
      _ <- checkActiveScenariosLimits(scenarioMetadata, command.user, deploymentUpdateStrategy) {
        IO.fromFuture(IO {
          val result = for {
            _ <- validateDeployment(scenarioMetadata, scenarioGraphVersion, command.user)
            // We keep deployments metrics (used by counts mechanism) keyed by scenario name.
            // Because of that we can't run more than one deployment for scenario in a time.
            // TODO: We should key metrics by deployment id and remove this limitation
            // Saving of deployment is the final step before deployment request because we want to store only requested deployments
            _ <- saveDeploymentEnsuringNoConcurrentDeploymentsForScenario(
              command,
              scenarioMetadata,
              scenarioGraphVersion.id
            )
            _ <- runDeploymentUsingDeploymentManagerAsync(
              scenarioMetadata,
              scenarioGraphVersion,
              command,
              deploymentUpdateStrategy
            )
          } yield ()
          result.value
        })
      }
    } yield DeploymentForeignKeys(scenarioMetadata.id, scenarioGraphVersion.id)).value

  private def validateDeployment(
      scenarioMetadata: ScenarioMetadata,
      scenarioGraphVersion: ProcessVersionEntityData,
      deployer: LoggedUser
  ) = {
    for {
      _ <- EitherT.cond[Future](!scenarioMetadata.isFragment, (), DeploymentOfFragmentError)
      _ <- EitherT.cond[Future](!scenarioMetadata.isArchived, (), DeploymentOfArchivedScenarioError)
      _ <- runDeploymentManagerSpecificValidations(scenarioMetadata, scenarioGraphVersion, deployer)
    } yield ()
  }

  private def getScenarioGraphVersion(scenarioMetadata: ScenarioMetadata, deployer: LoggedUser) = {
    EitherT(
      scenarioGraphVersionService.getValidResolvedLatestScenarioGraphVersion(scenarioMetadata, deployer)
    ).leftMap[RunDeploymentError](error => ScenarioGraphValidationError(error.errors, error.nodeNamesById))
  }

  private def getScenarioMetadata(
      command: RunDeploymentCommand
  ): EitherT[Future, RunDeploymentError, ScenarioMetadata] =
    EitherT.fromOptionF(
      dbioRunner.run(scenarioMetadataRepository.getScenarioMetadata(command.scenarioName)),
      ScenarioNotFoundError(command.scenarioName)
    )

  private def saveDeploymentEnsuringNoConcurrentDeploymentsForScenario(
      command: RunDeploymentCommand,
      scenarioMetadata: ScenarioMetadata,
      scenarioVersion: VersionId
  ): EitherT[Future, RunDeploymentError, Unit] = {
    EitherT(dbioRunner.runInSerializableTransactionWithRetry((for {
      nonFinishedDeployments <- getConcurrentlyPerformedDeploymentsForScenario(scenarioMetadata)
      _                      <- checkNoConcurrentDeploymentsForScenario(nonFinishedDeployments, scenarioMetadata.name)
      _ = logger.debug(s"Saving deployment: ${command.id}")
      _ <- saveDeployment(command, scenarioMetadata, scenarioVersion)
    } yield ()).value))
  }

  private def getConcurrentlyPerformedDeploymentsForScenario(scenarioMetadata: ScenarioMetadata) = {
    val nonPerformingDeploymentStatuses =
      Set(
        DeploymentStatus.Canceled.name,
        DeploymentStatusName.finishedStatusName,
        DeploymentStatusName.problemStatusName
      )
    EitherT.right(
      deploymentRepository.getScenarioDeploymentsInNotMatchingStatus(
        scenarioMetadata.id,
        nonPerformingDeploymentStatuses
      )
    )
  }

  private def checkNoConcurrentDeploymentsForScenario(
      nonFinishedDeployments: Seq[DeploymentEntityData],
      scenarioName: ProcessName
  ) = {
    EitherT.fromEither(
      NonEmptyList
        .fromList(nonFinishedDeployments.toList)
        .map(conflictingDeployments =>
          Left(ConcurrentDeploymentsForScenarioArePerformedError(scenarioName, conflictingDeployments.map(_.id)))
        )
        .getOrElse(Right(()))
    )
  }

  private def saveDeployment(
      command: RunDeploymentCommand,
      scenarioMetadata: ScenarioMetadata,
      scenarioVersion: VersionId
  ): EitherT[DB, RunDeploymentError, Unit] = {
    val now = Timestamp.from(clock.instant())
    EitherT(
      deploymentRepository.saveDeployment(
        DeploymentEntityData(
          command.id,
          scenarioMetadata.id,
          now,
          command.user.id,
          WithModifiedAt(DeploymentStatus.DuringDeploy(scenarioVersion), now)
        )
      )
    ).leftMap(e => ConflictingDeploymentIdError(e.id))
  }

  private def checkActiveScenariosLimits(
      scenarioMetadata: ScenarioMetadata,
      deployer: LoggedUser,
      deploymentUpdateStrategy: DeploymentUpdateStrategy
  )(action: IO[Either[RunDeploymentError, Unit]]): EitherT[Future, RunDeploymentError, Unit] = EitherT {
    implicit val loggedUser: LoggedUser = deployer
    limitsService
      .checkActiveScenarioLimitsBeforeDeployment(
        scenarioMetadata.name,
        scenarioMetadata.processingType,
        deploymentUpdateStrategy
      )(action)
      .map {
        case Left(LimitError.MaxActiveScenariosCountExceededError(limit)) =>
          Left(MaxActiveScenariosCountExceededError(limit))
        case Right(result) => result
      }
      .unsafeToFuture()
  }

  private def runDeploymentManagerSpecificValidations(
      scenarioMetadata: ScenarioMetadata,
      scenarioGraphVersion: ProcessVersionEntityData,
      user: LoggedUser
  ): EitherT[Future, RunDeploymentError, Unit] = {
    val runtimeVersionData = processVersionFor(scenarioMetadata, scenarioGraphVersion)
    // TODO: It shouldn't be needed
    val dummyDeploymentData = createDeploymentData(
      LegacyDeploymentId(""),
      user,
      NodesDeploymentData.empty,
      scenarioMetadata.processingType
    )
    for {
      result <- EitherT[Future, RunDeploymentError, Unit](
        dmDispatcher
          .deploymentManagerUnsafe(scenarioMetadata.processingType)(user)
          .processCommand(
            DMValidateScenarioCommand(
              runtimeVersionData,
              dummyDeploymentData,
              scenarioGraphVersion.jsonUnsafe,
              DeploymentUpdateStrategy.DontReplaceDeployment
            )
          )
          .map(_ => Right(()))
          // TODO: more explicit way to pass errors from DM
          .recover { case NonFatal(ex) =>
            Left(DeployValidationError(ex.getMessage))
          }
      )
    } yield result
  }

  private def runDeploymentUsingDeploymentManagerAsync(
      scenarioMetadata: ScenarioMetadata,
      scenarioGraphVersion: ProcessVersionEntityData,
      command: RunDeploymentCommand,
      deploymentUpdateStrategy: DeploymentUpdateStrategy
  ): EitherT[Future, RunDeploymentError, Unit] = {
    val runtimeVersionData = processVersionFor(scenarioMetadata, scenarioGraphVersion)
    val deploymentData = createDeploymentData(
      toLegacyDeploymentId(command.id),
      command.user,
      command.nodesDeploymentData,
      scenarioMetadata.processingType
    )
    dmDispatcher
      .deploymentManagerUnsafe(scenarioMetadata.processingType)(command.user)
      .processCommand(
        DMRunDeploymentCommand(
          runtimeVersionData,
          deploymentData,
          scenarioGraphVersion.jsonUnsafe.withoutUnsafeFields,
          deploymentUpdateStrategy
        )
      )
      .map { externalDeploymentId =>
        logger.debug(
          s"Deployment [${command.id}] successfully requested. External deployment id is: $externalDeploymentId"
        )
      }
      .failed
      .foreach(handleFailureDuringDeploymentRequesting(command.id, _))
    EitherT.pure(())
  }

  private def createDeploymentData(
      deploymentId: LegacyDeploymentId,
      loggedUser: LoggedUser,
      nodesData: NodesDeploymentData,
      processingType: ProcessingType
  ) = DeploymentData(
    deploymentId = deploymentId,
    user = loggedUser.toManagerUser,
    additionalDeploymentData = Map.empty,
    nodesData = nodesData,
    additionalModelConfigs = getAdditionalModelConfigsRequiredForRuntime(processingType, loggedUser)
  )

  private def handleFailureDuringDeploymentRequesting(
      deploymentId: DeploymentId,
      ex: Throwable
  ): Unit = {
    logger.warn(s"Deployment [$deploymentId] requesting finished with failure. Status will be marked as PROBLEM", ex)
    dbioRunner
      .run(
        deploymentRepository.updateDeploymentStatus(
          deploymentId,
          DeploymentStatus.Problem.FailureDuringDeploymentRequesting
        )
      )
      .failed
      .foreach { ex =>
        logger.warn(s"Exception during marking deployment [$deploymentId] status as PROBLEM", ex)
      }
  }

  private def toLegacyDeploymentId(id: DeploymentId) = {
    LegacyDeploymentId(id.toString)
  }

  private def getDeploymentById(
      id: DeploymentId
  ): EitherT[Future, GetDeploymentStatusError, DeploymentRepository.DeploymentWithScenarioMetadata] =
    EitherT.fromOptionF(dbioRunner.run(deploymentRepository.getDeploymentById(id)), DeploymentNotFoundError(id))

  private def checkPermission[F[_]: Applicative, Error >: NoPermissionError.type](
      user: LoggedUser,
      category: String,
      permission: Permission
  ): EitherT[F, Error, Unit] =
    EitherT.cond[F](user.can(category, permission), (), NoPermissionError)

  private def processVersionFor(scenarioMetadata: ScenarioMetadata, scenarioGraphVersion: ProcessVersionEntityData) = {
    RuntimeVersionData(
      versionId = scenarioGraphVersion.id,
      processName = scenarioMetadata.name,
      processId = scenarioMetadata.id,
      labels = scenarioMetadata.labels.map(_.value),
      user = scenarioGraphVersion.user,
      modelVersion = scenarioGraphVersion.modelVersion
    )
  }

  private def getAdditionalModelConfigsRequiredForRuntime(processingType: ProcessingType, loggedUser: LoggedUser) = {
    AdditionalModelConfigs(
      AdditionalComponentConfigsForRuntimeExtractor.getRequiredAdditionalConfigsForRuntime(
        additionalComponentConfigs.forProcessingType(processingType)(loggedUser).getOrElse(Map.empty)
      )
    )
  }

}

object DeploymentService {

  final case class DeploymentForeignKeys(scenarioId: ProcessId, scenarioGraphVersionId: VersionId)

  sealed trait RunDeploymentError

  sealed trait GetDeploymentStatusError

  final case class ConflictingDeploymentIdError(id: DeploymentId) extends RunDeploymentError

  final case class ConcurrentDeploymentsForScenarioArePerformedError(
      scenarioName: ProcessName,
      concurrentDeploymentsIds: NonEmptyList[DeploymentId]
  ) extends RunDeploymentError

  final case class ScenarioNotFoundError(scenarioName: ProcessName) extends RunDeploymentError

  case object DeploymentOfFragmentError extends RunDeploymentError

  case object DeploymentOfArchivedScenarioError extends RunDeploymentError

  final case class MaxActiveScenariosCountExceededError(maxActiveScenariosCount: Int) extends RunDeploymentError

  final case class DeploymentNotFoundError(id: DeploymentId) extends GetDeploymentStatusError

  case object NoPermissionError extends RunDeploymentError with GetDeploymentStatusError

  final case class ScenarioGraphValidationError(
      errors: ValidationErrors,
      nodeNamesById: Map[NodeId, String] = Map.empty
  ) extends RunDeploymentError

  final case class DeployValidationError(message: String) extends RunDeploymentError

}

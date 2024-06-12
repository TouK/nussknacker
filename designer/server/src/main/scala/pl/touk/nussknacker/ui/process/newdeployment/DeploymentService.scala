package pl.touk.nussknacker.ui.process.newdeployment

import cats.Applicative
import cats.data.EitherT
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleDeploymentStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{ProcessVersion => RuntimeVersionData}
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId => LegacyDeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.{DeploymentEntityData, WithModifiedAt}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentService._
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ScenarioMetadataRepository}
import pl.touk.nussknacker.ui.process.version.ScenarioGraphVersionService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.sql.Timestamp
import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal

// TODO: This class is a new version of deployment.DeploymentService. The problem with the old one is that
//       it joins multiple responsibilities like activity log (currently called "actions") and deployments management.
//       Also, because of the fact that periodic mechanism is build as a plug-in (DeploymentManager), some deployment related
//       operations (run now operation) is modeled as a CustomAction. Eventually, we should:
//       - Split activity log and deployments management
//       - Move periodic mechanism into to the designer's core
//       - Remove CustomAction
//       After we do this, we can remove legacy classes and fully switch to the new once.
class DeploymentService(
    scenarioMetadataRepository: ScenarioMetadataRepository,
    scenarioGraphVersionService: ScenarioGraphVersionService,
    deploymentRepository: DeploymentRepository,
    dmDispatcher: DeploymentManagerDispatcher,
    dbioRunner: DBIOActionRunner,
    clock: Clock
)(implicit ec: ExecutionContext) {

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

  def runDeployment(command: RunDeploymentCommand): DB[Either[RunDeploymentError, DeploymentForeignKeys]] =
    (for {
      scenarioMetadata <- getScenarioMetadata(command)
      _ <- checkPermission(
        user = command.user,
        category = scenarioMetadata.processCategory,
        permission = Permission.Deploy
      )
      _ <- saveDeployment(command, scenarioMetadata)
      scenarioGraphVersion <- EitherT(
        scenarioGraphVersionService.getValidResolvedLatestScenarioGraphVersion(scenarioMetadata, command.user)
      ).leftMap[RunDeploymentError](error => ScenarioGraphValidationError(error.errors))
      _ <- validateUsingDeploymentManager(scenarioMetadata, scenarioGraphVersion, command.user)
      _ <- runDeploymentUsingDeploymentManager(scenarioMetadata, scenarioGraphVersion, command)
    } yield DeploymentForeignKeys(scenarioMetadata.id, scenarioGraphVersion.id)).value

  private def getScenarioMetadata(command: RunDeploymentCommand): EitherT[DB, RunDeploymentError, ProcessEntityData] =
    EitherT.fromOptionF(
      scenarioMetadataRepository.getScenarioMetadata(command.scenarioName),
      ScenarioNotFoundError(command.scenarioName)
    )

  private def saveDeployment(
      command: RunDeploymentCommand,
      scenarioMetadata: ProcessEntityData
  ): EitherT[DB, RunDeploymentError, Unit] = {
    val now = Timestamp.from(clock.instant())
    EitherT(
      deploymentRepository.saveDeployment(
        DeploymentEntityData(
          command.id,
          scenarioMetadata.id,
          now,
          command.user.id,
          WithModifiedAt(SimpleDeploymentStatus.DuringDeploy, now)
        )
      )
    ).leftMap(e => ConflictingDeploymentIdError(e.id))
  }

  private def validateUsingDeploymentManager(
      scenarioMetadata: ProcessEntityData,
      scenarioGraphVersion: ProcessVersionEntityData,
      user: LoggedUser
  ): EitherT[DB, RunDeploymentError, Unit] = {
    val runtimeVersionData = RuntimeVersionData(
      versionId = scenarioGraphVersion.id,
      processName = scenarioMetadata.name,
      processId = scenarioMetadata.id,
      user = scenarioGraphVersion.user,
      modelVersion = scenarioGraphVersion.modelVersion
    )
    // TODO: It shouldn't be needed
    val dumbDeploymentData = DeploymentData(
      LegacyDeploymentId(""),
      user.toManagerUser,
      Map.empty,
      NodesDeploymentData.empty
    )
    for {
      result <- EitherT[DB, RunDeploymentError, Unit](
        toEffectAll(
          DB.from(
            dmDispatcher
              .deploymentManagerUnsafe(scenarioMetadata.processingType)(user)
              .processCommand(
                DMValidateScenarioCommand(
                  runtimeVersionData,
                  dumbDeploymentData,
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
        )
      )
    } yield result
  }

  private def runDeploymentUsingDeploymentManager(
      scenarioMetadata: ProcessEntityData,
      scenarioGraphVersion: ProcessVersionEntityData,
      command: RunDeploymentCommand
  ): EitherT[DB, RunDeploymentError, Option[ExternalDeploymentId]] = {
    val runtimeVersionData = RuntimeVersionData(
      versionId = scenarioGraphVersion.id,
      processName = scenarioMetadata.name,
      processId = scenarioMetadata.id,
      user = scenarioGraphVersion.user,
      modelVersion = scenarioGraphVersion.modelVersion
    )
    val deploymentData = DeploymentData(
      toLegacyDeploymentId(command.id),
      command.user.toManagerUser,
      additionalDeploymentData = Map.empty,
      command.nodesDeploymentData
    )
    EitherT.right(
      toEffectAll(
        DB.from(
          dmDispatcher
            .deploymentManagerUnsafe(scenarioMetadata.processingType)(command.user)
            .processCommand(
              DMRunDeploymentCommand(
                runtimeVersionData,
                deploymentData,
                scenarioGraphVersion.jsonUnsafe,
                DeploymentUpdateStrategy.DontReplaceDeployment
              )
            )
        )
      )
    )
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

}

object DeploymentService {

  final case class DeploymentForeignKeys(scenarioId: ProcessId, scenarioGraphVersionId: VersionId)

  sealed trait RunDeploymentError

  sealed trait GetDeploymentStatusError

  final case class ConflictingDeploymentIdError(id: DeploymentId) extends RunDeploymentError

  final case class ScenarioNotFoundError(scenarioName: ProcessName) extends RunDeploymentError

  final case class DeploymentNotFoundError(id: DeploymentId) extends GetDeploymentStatusError

  case object NoPermissionError extends RunDeploymentError with GetDeploymentStatusError

  final case class ScenarioGraphValidationError(errors: ValidationErrors) extends RunDeploymentError

  final case class DeployValidationError(message: String) extends RunDeploymentError

}

package pl.touk.nussknacker.ui.process.newdeployment

import cats.Applicative
import cats.data.EitherT
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{
  DMRunDeploymentCommand,
  DMValidateScenarioCommand,
  DataFreshnessPolicy
}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{ProcessVersion => RuntimeVersionData}
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId => LegacyDeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.newactivity.ActivityService.CommentForeignKeys
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.DeploymentEntityData
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
  )(implicit loggedUser: LoggedUser): Future[Either[GetDeploymentStatusError, StatusName]] =
    (for {
      deploymentWithScenarioMetadata <- getDeploymentById(id)
      _ <- checkPermission[Future](
        user = loggedUser,
        category = deploymentWithScenarioMetadata.scenarioMetadata.processCategory,
        permission = Permission.Read
      )
      status <- getScenarioStatus(id, deploymentWithScenarioMetadata.scenarioMetadata)
    } yield status.name).value

  def runDeployment(
      command: RunDeploymentCommand
  )(saveComment: CommentForeignKeys => DB[Unit]): Future[Either[RunDeploymentError, Unit]] =
    dbioRunner.run(
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
        _ <- EitherT.right(saveComment(CommentForeignKeys(scenarioMetadata.id, scenarioGraphVersion.id)))
        _ <- runDeploymentUsingDeploymentManager(scenarioMetadata, scenarioGraphVersion, command)
      } yield ()).value
    )

  private def getScenarioMetadata(command: RunDeploymentCommand): EitherT[DB, RunDeploymentError, ProcessEntityData] =
    EitherT.fromOptionF(
      scenarioMetadataRepository.getScenarioMetadata(command.scenarioName),
      ScenarioNotFoundError(command.scenarioName)
    )

  private def saveDeployment(
      command: RunDeploymentCommand,
      scenarioMetadata: ProcessEntityData
  ): EitherT[DB, RunDeploymentError, Unit] =
    EitherT(
      deploymentRepository.saveDeployment(
        DeploymentEntityData(command.id, scenarioMetadata.id, Timestamp.from(clock.instant()), command.user.id)
      )
    ).leftMap(e => ConflictingDeploymentIdError(e.id))

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
                  scenarioGraphVersion.jsonUnsafe
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
                savepointPath = None
              )
            )
        )
      )
    )
  }

  private def toLegacyDeploymentId(id: DeploymentId) = {
    LegacyDeploymentId(id.toString)
  }

  private def getDeploymentById(id: DeploymentId) =
    EitherT.fromOptionF(dbioRunner.run(deploymentRepository.getDeploymentById(id)), DeploymentNotFoundError(id))

  private def checkPermission[F[_]: Applicative](user: LoggedUser, category: String, permission: Permission) =
    EitherT.cond[F](user.can(category, permission), (), NoPermissionError)

  private def getScenarioStatus(deploymentId: DeploymentId, scenarioMetadata: ProcessEntityData)(
      implicit loggedUser: LoggedUser
  ) = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    EitherT.right[GetDeploymentStatusError](
      dmDispatcher
        .deploymentManagerUnsafe(scenarioMetadata.processingType)
        .getProcessStates(scenarioMetadata.name)
        .map { result =>
          val legacyDeploymentId = toLegacyDeploymentId(deploymentId)
          result.value
            .find(_.deploymentId.contains(legacyDeploymentId))
            .map(_.status)
            // TODO: Smarter statuses
            .getOrElse(SimpleStateStatus.NotDeployed)
        }
    )
  }

}

object DeploymentService {

  sealed trait RunDeploymentError

  sealed trait GetDeploymentStatusError

  final case class ConflictingDeploymentIdError(id: DeploymentId) extends RunDeploymentError

  final case class ScenarioNotFoundError(scenarioName: ProcessName) extends RunDeploymentError

  final case class DeploymentNotFoundError(id: DeploymentId) extends GetDeploymentStatusError

  case object NoPermissionError extends RunDeploymentError with GetDeploymentStatusError

  final case class ScenarioGraphValidationError(errors: ValidationErrors) extends RunDeploymentError

  final case class DeployValidationError(message: String) extends RunDeploymentError

}

package pl.touk.nussknacker.ui.process.newdeployment

import cats.Applicative
import cats.data.EitherT
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.process.deployment.{
  CommonCommandData,
  DeploymentService => LegacyDeploymentService,
  RunDeploymentCommand => LegacyRunDeploymentCommand
}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.DeploymentEntityData
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentService._
import pl.touk.nussknacker.ui.process.repository.{CommentValidationError, DBIOActionRunner, ScenarioMetadataRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.sql.Timestamp
import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

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
    deploymentRepository: DeploymentRepository,
    // TODO: we shouldn't call legacy service, instead we should new service from the legacy one
    legacyDeploymentService: LegacyDeploymentService,
    dbioRunner: DBIOActionRunner,
    clock: Clock
)(implicit ec: ExecutionContext) {

  def processCommand(
      command: DeploymentCommand
  ): Future[Either[RunDeploymentError, Unit]] =
    command match {
      case command: RunDeploymentCommand =>
        runDeployment(command)
    }

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
      // TODO: We should check deployment status instead scenario state but before that we should pass correct deployment id
      scenarioState <- getScenarioStatus(deploymentWithScenarioMetadata.scenarioMetadata)
    } yield scenarioState.status.name).value

  private def runDeployment(
      command: RunDeploymentCommand
  ): Future[Either[RunDeploymentError, Unit]] =
    dbioRunner.run(
      (for {
        scenarioMetadata <- getScenarioMetadata(command)
        _ <- checkPermission(
          user = command.user,
          category = scenarioMetadata.processCategory,
          permission = Permission.Deploy
        )
        _         <- saveDeployment(command, scenarioMetadata)
        runResult <- invokeLegacyRunDeploymentLogic(command, scenarioMetadata)
      } yield runResult).value
    )

  private def getScenarioMetadata(command: RunDeploymentCommand) =
    EitherT.fromOptionF(
      scenarioMetadataRepository.getScenarioMetadata(command.scenarioName),
      ScenarioNotFoundError(command.scenarioName)
    )

  private def saveDeployment(command: RunDeploymentCommand, scenarioMetadata: ProcessEntityData) =
    EitherT(
      deploymentRepository.saveDeployment(
        DeploymentEntityData(command.id, scenarioMetadata.id, Timestamp.from(clock.instant()), command.user.id)
      )
    ).leftMap(e => ConflictingDeploymentIdError(e.id))

  private def invokeLegacyRunDeploymentLogic(command: RunDeploymentCommand, scenarioMetadata: ProcessEntityData) =
    EitherT[DB, RunDeploymentError, Unit](
      DB.from(
        // TODO: Currently it doesn't use our deploymentId. Instead, it uses action id
        legacyDeploymentService
          .processCommand(
            LegacyRunDeploymentCommand(
              CommonCommandData(
                ProcessIdWithName(scenarioMetadata.id, command.scenarioName),
                command.comment,
                command.user
              ),
              savepointPath = None,
              nodesDeploymentData = command.nodesDeploymentData,
            )
          )
          .transform { result =>
            result
              .map[Either[RunDeploymentError, Unit]](_ => Right(()))
              .recover { case CommentValidationError(msg) =>
                Left(NewCommentValidationError(msg))
              }
          }
      )
    )

  private def getDeploymentById(id: DeploymentId) =
    EitherT.fromOptionF(dbioRunner.run(deploymentRepository.getDeploymentById(id)), DeploymentNotFoundError(id))

  private def checkPermission[F[_]: Applicative](user: LoggedUser, category: String, permission: Permission) =
    EitherT.cond[F](user.can(category, permission), (), NoPermissionError)

  private def getScenarioStatus(scenarioMetadata: ProcessEntityData)(implicit loggedUser: LoggedUser) = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    EitherT.right[GetDeploymentStatusError](
      legacyDeploymentService.getProcessState(ProcessIdWithName(scenarioMetadata.id, scenarioMetadata.name))
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

  final case class NewCommentValidationError(message: String) extends RunDeploymentError

}

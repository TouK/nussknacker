package pl.touk.nussknacker.ui.process.deployment

import cats.data.EitherT
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.error.{
  CommentValidationErrorNG,
  GetDeploymentStatusError,
  NoPermissionError,
  RunDeploymentError
}
import pl.touk.nussknacker.ui.process.deployment.DeploymentEntityFactory.DeploymentEntityData
import pl.touk.nussknacker.ui.process.repository.{CommentValidationError, DBIOActionRunner, ScenarioRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.sql.Timestamp
import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

// TODO: we should replace DeploymentService by this class after we split Deployments and Activities
class NewDeploymentService(
    scenariosRepository: ScenarioRepository,
    deploymentRepository: DeploymentRepository,
    // TODO: we shouldn't call legacy service, instead we should new service from the legacy one
    legacyDeploymentService: DeploymentService,
    dbioRunner: DBIOActionRunner,
    clock: Clock
)(implicit ec: ExecutionContext) {

  def processCommand(
      command: DeploymentCommand
  ): Future[Either[RunDeploymentError, Future[Option[ExternalDeploymentId]]]] = {
    command match {
      case command: NewRunDeploymentCommand =>
        runDeployment(command)
    }
  }

  private def runDeployment(
      command: NewRunDeploymentCommand
  ): Future[Either[RunDeploymentError, Future[Option[ExternalDeploymentId]]]] = {
    dbioRunner.runInTransactionE(
      (for {
        scenarioMetadata <- scenariosRepository.getScenarioMetadata(command.scenarioName)
        _ <- EitherT.fromEither(
          Either.cond(command.user.can(scenarioMetadata.processCategory, Permission.Deploy), (), NoPermissionError)
        )
        _ <- EitherT.right(
          deploymentRepository.saveDeployment(
            DeploymentEntityData(command.id, scenarioMetadata.id, Timestamp.from(clock.instant()), command.user.id)
          )
        )
        // TODO: Currently it doesn't use our deploymentId. Instead, it uses action id
        runResult <- invokeLegacyRunDeploymentLogic(command, scenarioMetadata)
      } yield runResult).value
    )
  }

  private def invokeLegacyRunDeploymentLogic(
      command: NewRunDeploymentCommand,
      scenarioMetadata: ProcessEntityData
  ): EitherT[DB, RunDeploymentError, Future[Option[ExternalDeploymentId]]] = {
    EitherT(
      toEffectAll(
        DB.from(
          legacyDeploymentService.processCommand(
            RunDeploymentCommand(
              CommonCommandData(
                ProcessIdWithName(scenarioMetadata.id, command.scenarioName),
                command.comment,
                command.user
              ),
              savepointPath = None,
              nodesDeploymentData = command.nodesDeploymentData,
            )
          )
        ).asTry
          .map(
            _.map[Either[RunDeploymentError, Future[Option[ExternalDeploymentId]]]](Right(_))
              .recover { case CommentValidationError(msg) =>
                Left(CommentValidationErrorNG(msg))
              }
              .get
          )
      )
    )
  }

  def getDeploymentStatus(
      id: NewDeploymentId
  )(implicit loggedUser: LoggedUser): Future[Either[GetDeploymentStatusError, StatusName]] =
    dbioRunner.runInTransactionE(
      (for {
        deploymentWithScenarioMetadata <- EitherT[DB, GetDeploymentStatusError, DeploymentWithScenarioMetadata](
          deploymentRepository.getDeploymentById(id)
        )
        DeploymentWithScenarioMetadata(_, scenarioMetadata) = deploymentWithScenarioMetadata
        _ <- EitherT.cond(loggedUser.can(scenarioMetadata.processCategory, Permission.Read), (), NoPermissionError)
        // TODO: We should check deployment state instead scenario state but before that we should pass correct deployment id
        scenarioStatus <- EitherT.right[GetDeploymentStatusError](toEffectAll(DB.from {
          implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
          legacyDeploymentService.getProcessState(ProcessIdWithName(scenarioMetadata.id, scenarioMetadata.name))
        }))
      } yield scenarioStatus.status.name).value
    )

}

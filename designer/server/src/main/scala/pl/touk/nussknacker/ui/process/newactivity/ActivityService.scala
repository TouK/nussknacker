package pl.touk.nussknacker.ui.process.newactivity

import cats.data.EitherT
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.newactivity.ActivityService._
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentService.RunDeploymentError
import pl.touk.nussknacker.ui.process.newdeployment.{DeploymentService, RunDeploymentCommand}
import pl.touk.nussknacker.ui.process.repository.DeploymentComment
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

// TODO: This service in the future should handle all activities that modify anything in application.
//       These activities should be stored in the dedicated table (not in comments table)
class ActivityService(
    deploymentCommentSettings: Option[DeploymentCommentSettings],
    commentRepository: CommentRepository,
    deploymentService: DeploymentService
)(implicit ec: ExecutionContext) {

  def processCommand[Command, ErrorType](command: Command, comment: Option[Comment])(
      implicit toActivityCommandConverter: ToActivityCommandConverter[Command, ErrorType]
  ): Future[Either[ActivityError[ErrorType], Unit]] = {
    toActivityCommandConverter.convert(command) match {
      case RunDeploymentActivityCommand(command) =>
        (for {
          validatedCommentOpt <- validateDeploymentComment(comment)
          _                   <- runDeployment(command, validatedCommentOpt)
        } yield ()).value
    }
  }

  private def validateDeploymentComment(comment: Option[Comment]) = {
    EitherT
      .fromEither[Future](
        DeploymentComment
          .createDeploymentComment(comment, deploymentCommentSettings)
          .toEither
      )
      .leftMap[ActivityError[RunDeploymentError]](err => CommentValidationError(err.message))
  }

  private def runDeployment(command: RunDeploymentCommand, validatedCommentOpt: Option[DeploymentComment]) = {
    EitherT(deploymentService.runDeployment(command) { keys =>
      saveCommentIfNeeded(validatedCommentOpt.map(_.toComment(ScenarioActionName.Deploy)), keys, command.user)
    }).leftMap[ActivityError[RunDeploymentError]](UnderlyingServiceError(_))
  }

  // TODO: Use dedicated activity repository / table
  private def saveCommentIfNeeded(commentOpt: Option[Comment], keys: CommentForeignKeys, user: LoggedUser) = {
    commentOpt
      .map(commentRepository.saveComment(keys.processId, keys.scenarioGraphVersionId, user, _))
      .getOrElse(DB.successful(()))
  }

}

object ActivityService {

  sealed trait ActivityCommand[Command, ErrorType] {
    def underlyingCommand: Command
  }

  trait ToActivityCommandConverter[Command, ErrorType] {
    def convert(command: Command): ActivityCommand[Command, ErrorType]
  }

  implicit val fromRunDeploymentCommand: ToActivityCommandConverter[RunDeploymentCommand, RunDeploymentError] =
    (command: RunDeploymentCommand) => RunDeploymentActivityCommand(command)

  private final case class RunDeploymentActivityCommand(underlyingCommand: RunDeploymentCommand)
      extends ActivityCommand[RunDeploymentCommand, RunDeploymentError] {}

  trait ActivityError[+UnderlyingServiceErrorType]

  final case class UnderlyingServiceError[UnderlyingServiceErrorType](error: UnderlyingServiceErrorType)
      extends ActivityError[UnderlyingServiceErrorType]

  final case class CommentValidationError(message: String) extends ActivityError[Nothing]

  case class CommentForeignKeys(processId: ProcessId, scenarioGraphVersionId: VersionId)

}

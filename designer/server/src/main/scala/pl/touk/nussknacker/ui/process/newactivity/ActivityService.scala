package pl.touk.nussknacker.ui.process.newactivity

import cats.data.EitherT
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.newactivity.ActivityService._
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentService.RunDeploymentError
import pl.touk.nussknacker.ui.process.newdeployment.{DeploymentService, RunDeploymentCommand}
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, DeploymentComment}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

// TODO: This service in the future should handle all activities that modify anything in application.
//       These activities should be stored in the dedicated table (not in comments table)
class ActivityService(
    deploymentCommentSettings: Option[DeploymentCommentSettings],
    commentRepository: CommentRepository,
    deploymentService: DeploymentService,
    dbioRunner: DBIOActionRunner
)(implicit ec: ExecutionContext) {

  def processCommand[Command, ErrorType](command: Command, comment: Option[Comment])(
      implicit toActivityCommandConverter: ToActivityCommandConverter[Command, ErrorType]
  ): Future[Either[ActivityError[ErrorType], Unit]] = {
    toActivityCommandConverter.convert(command) match {
      case RunDeploymentActivityCommand(command) =>
        dbioRunner.run(
          (for {
            validatedCommentOpt <- validateDeploymentCommentWhenPassed(comment)
            keys                <- runDeployment(command)
            _ <- saveCommentWhenPassed[RunDeploymentError](
              validatedCommentOpt,
              keys.scenarioId,
              keys.scenarioGraphVersionId,
              command.user
            )
          } yield ()).value
        )
    }
  }

  private def validateDeploymentCommentWhenPassed(comment: Option[Comment]) = {
    EitherT
      .fromEither[DB](
        DeploymentComment
          .createDeploymentComment(comment, deploymentCommentSettings)
          .toEither
      )
      .map(_.map(_.toComment(ScenarioActionName.Deploy)))
      .leftMap[ActivityError[RunDeploymentError]](err => CommentValidationError(err.message))
  }

  private def runDeployment(command: RunDeploymentCommand) =
    EitherT(deploymentService.runDeployment(command))
      .leftMap[ActivityError[RunDeploymentError]](UnderlyingServiceError(_))

  private def saveCommentWhenPassed[ErrorType](
      commentOpt: Option[Comment],
      scenarioId: ProcessId,
      scenarioGraphVersionId: VersionId,
      user: LoggedUser
  ) = {
    EitherT.right[ActivityError[ErrorType]](
      commentOpt
        .map(commentRepository.saveComment(scenarioId, scenarioGraphVersionId, user, _))
        .getOrElse(DB.successful(()))
    )
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

}

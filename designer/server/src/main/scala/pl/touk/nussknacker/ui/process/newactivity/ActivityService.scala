package pl.touk.nussknacker.ui.process.newactivity

import cats.data.EitherT
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.newactivity.ActivityService._
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentService.RunDeploymentError
import pl.touk.nussknacker.ui.process.newdeployment.{DeploymentService, RunDeploymentCommand}
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, DeploymentComment}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

// TODO: This service in the future should handle all activities that modify anything in application.
//       These activities should be stored in the dedicated table (not in comments table)
class ActivityService(
    deploymentCommentSettings: Option[DeploymentCommentSettings],
    scenarioActivityRepository: ScenarioActivityRepository,
    deploymentService: DeploymentService,
    dbioRunner: DBIOActionRunner
)(implicit ec: ExecutionContext) {

  def processCommand[Command, ErrorType](command: Command, comment: Option[Comment])(
      implicit toActivityCommandConverter: ToActivityCommandConverter[Command, ErrorType]
  ): Future[Either[ActivityError[ErrorType], Unit]] = {
    toActivityCommandConverter.convert(command) match {
      case RunDeploymentActivityCommand(command) =>
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
    }
  }

  private def validateDeploymentCommentWhenPassed(
      comment: Option[Comment]
  ): EitherT[Future, ActivityError[RunDeploymentError], Option[Comment]] = EitherT.fromEither {
    DeploymentComment
      .createDeploymentComment(comment, deploymentCommentSettings)
      .toEither
      .left
      .map[ActivityError[RunDeploymentError]](err => CommentValidationError(err.message))
  }

  private def runDeployment(command: RunDeploymentCommand) =
    EitherT(deploymentService.runDeployment(command))
      .leftMap[ActivityError[RunDeploymentError]](UnderlyingServiceError(_))

  private def saveCommentWhenPassed[ErrorType](
      commentOpt: Option[Comment],
      scenarioId: ProcessId,
      scenarioGraphVersionId: VersionId,
      user: LoggedUser
  ): EitherT[Future, ActivityError[ErrorType], Unit] = {

    EitherT.right[ActivityError[ErrorType]](
      Future.unit
// todo NU-1772 in progress
//      commentOpt
//        .map(comment =>
//          dbioRunner.run(scenarioActivityRepository.addComment(scenarioId, scenarioGraphVersionId, comment)(user))
//        )
//        .getOrElse(Future.unit)
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

package pl.touk.nussknacker.ui.process.repository

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.repository.DeploymentComment._

// TODO: it does not refer to "deployment" only, rename to ValidatedComment
class DeploymentComment private (value: Comment) {

  def toComment(actionName: ScenarioActionName): Comment = {
    // TODO: remove this prefixes after adding custom icons
    val prefix = actionName match {
      case ScenarioActionName.Deploy => PrefixDeployedDeploymentComment
      case ScenarioActionName.Cancel => PrefixCanceledDeploymentComment
      case ScenarioActionName.RunNow => PrefixRunNowDeploymentComment
      case _                         => NoPrefix
    }
    new Comment {
      override def value: String = prefix + DeploymentComment.this.value.value
    }
  }

}

object DeploymentComment {

  private val PrefixDeployedDeploymentComment = "Deployment: "
  private val PrefixCanceledDeploymentComment = "Stop: "
  private val PrefixRunNowDeploymentComment   = "Run now: "
  private val NoPrefix                        = ""

  def createDeploymentComment(
      comment: Option[Comment],
      deploymentCommentSettings: Option[DeploymentCommentSettings]
  ): Validated[CommentValidationError, Option[DeploymentComment]] = {

    (comment.filterNot(_.value.isEmpty), deploymentCommentSettings) match {
      case (None, Some(_)) =>
        Invalid(CommentValidationError("Comment is required."))
      case (None, None) =>
        Valid(None)
      case (Some(comment), Some(deploymentCommentSettings)) =>
        Validated.cond(
          comment.value.matches(deploymentCommentSettings.validationPattern),
          Some(new DeploymentComment(comment)),
          CommentValidationError(comment, deploymentCommentSettings)
        )
      case (Some(comment), None) =>
        Valid(Some(new DeploymentComment(comment)))
    }
  }

  def unsafe(comment: Comment): DeploymentComment = new DeploymentComment(comment)

}

final case class CommentValidationError(message: String) extends BadRequestError(message)

object CommentValidationError {

  def apply(comment: Comment, deploymentCommentSettings: DeploymentCommentSettings): CommentValidationError = {
    val suffix = deploymentCommentSettings.exampleComment match {
      case Some(exampleComment) =>
        s"Example comment: $exampleComment."
      case None =>
        s"Validation pattern: ${deploymentCommentSettings.validationPattern}"
    }
    new CommentValidationError(s"Bad comment format '${comment.value}'. " + suffix)
  }

}

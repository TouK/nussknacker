package pl.touk.nussknacker.ui.process.repository

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.repository.DeploymentComment._

class DeploymentComment private (value: String) {

  def toComment(actionType: ProcessActionType): Comment = {
    // TODO: remove this prefixes after adding custom icons
    val prefix = actionType match {
      case ProcessActionType.Deploy => PrefixDeployedDeploymentComment
      case ProcessActionType.Cancel => PrefixCanceledDeploymentComment
      case _                        => throw new AssertionError(s"Not supported deployment action type: $actionType")
    }
    new Comment {
      override def value: String = prefix + DeploymentComment.this.value
    }
  }

}

object DeploymentComment {

  private val PrefixDeployedDeploymentComment = "Deployment: "
  private val PrefixCanceledDeploymentComment = "Stop: "

  def createDeploymentComment(
      comment: Option[String],
      deploymentCommentSettings: Option[DeploymentCommentSettings]
  ): Validated[CommentValidationError, Option[DeploymentComment]] = {

    (comment.filterNot(_.isEmpty), deploymentCommentSettings) match {
      case (None, Some(_)) =>
        Invalid(CommentValidationError("Comment is required."))
      case (None, None) =>
        Valid(None)
      case (Some(comment), Some(deploymentCommentSettings)) =>
        Validated.cond(
          comment.matches(deploymentCommentSettings.validationPattern),
          Some(new DeploymentComment(comment)),
          CommentValidationError(comment, deploymentCommentSettings)
        )
      case (Some(comment), None) =>
        Valid(Some(new DeploymentComment(comment)))
    }
  }

  def unsafe(comment: String): DeploymentComment = new DeploymentComment(comment)

}

final case class CommentValidationError(message: String) extends Exception(message)

object CommentValidationError {

  def apply(comment: String, deploymentCommentSettings: DeploymentCommentSettings): CommentValidationError = {
    val suffix = deploymentCommentSettings.exampleComment match {
      case Some(exampleComment) =>
        s"Example comment: $exampleComment."
      case None =>
        s"Validation pattern: ${deploymentCommentSettings.validationPattern}"
    }
    new CommentValidationError(s"Bad comment format '$comment'. " + suffix)
  }

}

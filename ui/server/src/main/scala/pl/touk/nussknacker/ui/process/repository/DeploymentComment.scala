package pl.touk.nussknacker.ui.process.repository

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.ui.listener.Comment

class DeploymentComment private(override val value: String) extends Comment {

  def withPrefix(prefix: String): DeploymentComment = new DeploymentComment(prefix + value)
}

object DeploymentComment {

  def createDeploymentComment(comment: Option[String], deploymentCommentSettings: Option[DeploymentCommentSettings]): Validated[CommentValidationError, Option[DeploymentComment]] = {

    (comment.filterNot(_.isEmpty), deploymentCommentSettings) match {
      case (None, Some(_)) =>
        Invalid(CommentValidationError("Comment is required."))
      case (None, None) =>
        Valid(None)
      case (Some(comment), Some(deploymentCommentSettings)) =>
        Validated.cond(
          comment.matches(deploymentCommentSettings.validationPattern),
          Some(new DeploymentComment(comment)),
          CommentValidationError(comment, deploymentCommentSettings))
      case (Some(comment), None) =>
        Valid(Some(new DeploymentComment(comment)))
    }
  }

  def unsafe(comment: String): DeploymentComment = new DeploymentComment(comment)

}

case class CommentValidationError(message: String) extends Exception(message)

object CommentValidationError {
  def apply(comment: String, deploymentCommentSettings: DeploymentCommentSettings): CommentValidationError = {
    deploymentCommentSettings.exampleComment match {
      case Some(exampleComment) =>
        new CommentValidationError(s"Bad comment format '$comment'. Example comment: $exampleComment.")
      case None =>
        new CommentValidationError(s"Bad comment format '$comment'. Validation pattern: ${deploymentCommentSettings.validationPattern}")
    }
  }
}

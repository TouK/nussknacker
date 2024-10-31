package pl.touk.nussknacker.ui.process.repository

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.api.DeploymentCommentSettings
import pl.touk.nussknacker.engine.api.Comment

object DeploymentComment {

  def createDeploymentComment(
      comment: Option[Comment],
      deploymentCommentSettings: Option[DeploymentCommentSettings]
  ): Validated[CommentValidationError, Option[Comment]] = {

    (comment, deploymentCommentSettings) match {
      case (None, Some(_)) =>
        Invalid(CommentValidationError("Comment is required."))
      case (None, None) =>
        Valid(None)
      case (Some(comment), Some(deploymentCommentSettings)) =>
        Validated.cond(
          comment.content.matches(deploymentCommentSettings.validationPattern),
          Some(comment),
          CommentValidationError(comment, deploymentCommentSettings)
        )
      case (Some(comment), None) =>
        Valid(Some(comment))
    }
  }

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
    new CommentValidationError(s"Bad comment format '${comment.content}'. " + suffix)
  }

}

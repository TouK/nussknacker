package pl.touk.nussknacker.ui.process.deployment

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.ui.listener.DeploymentComment

object DeploymentCommentValidator {

  def createDeploymentComment(comment: Option[String], settings: Option[DeploymentCommentSettings]): Validated[CommentValidationError, Option[DeploymentComment]] = {

    (comment.filterNot(_.isEmpty), settings) match {
      case (None, Some(_)) =>
        Invalid(CommentValidationError("Comment is required."))
      case (None, None) =>
        Valid(None)
      case (Some(comment), Some(deploymentCommentSettings)) =>
        Validated.cond(
          comment.matches(deploymentCommentSettings.validationPattern),
          Some(DeploymentComment(comment)),
          CommentValidationError(comment, deploymentCommentSettings))
      case (Some(comment), None) =>
        Valid(Some(DeploymentComment(comment)))
    }
  }

  def unsafe(comment: String): DeploymentComment = DeploymentComment(comment)

}

case class CommentValidationError(message: String) extends Exception(message)

object CommentValidationError {
  def apply(comment: String, deploymentCommentSettings: DeploymentCommentSettings) = {
    deploymentCommentSettings.exampleComment match {
      case Some(exampleComment) =>
        new CommentValidationError(s"Bad comment format '$comment'. Example comment: $exampleComment.")
      case None =>
        new CommentValidationError(s"Bad comment format '$comment'.")
    }
  }
}

case class DeploymentCommentSettings(validationPattern: String, exampleComment: Option[String])

object DeploymentCommentSettings {
  def create(validationPattern: String, exampleComment: Option[String]): Validated[EmptyDeploymentCommentSettingsError, DeploymentCommentSettings] = {
    Validated.cond(validationPattern.nonEmpty,
      new DeploymentCommentSettings(validationPattern, exampleComment),
      EmptyDeploymentCommentSettingsError("Field validationPattern cannot be empty."))
  }

  def unsafe(validationPattern: String, exampleComment: Option[String]): DeploymentCommentSettings = {
    new DeploymentCommentSettings(validationPattern, exampleComment)
  }
}

case class EmptyDeploymentCommentSettingsError(message: String) extends Exception(message)

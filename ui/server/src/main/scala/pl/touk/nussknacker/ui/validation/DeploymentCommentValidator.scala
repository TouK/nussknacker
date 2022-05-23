package pl.touk.nussknacker.ui.validation

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.ui.listener.DeploymentComment

object DeploymentCommentValidator {

  def createDeploymentComment(comment: Option[String], settings: Option[DeploymentCommentSettings]): Validated[CommentValidationError, Option[DeploymentComment]] = {
    comment.filterNot(_.isEmpty) match {
      case None =>
        settings match {
          case Some(_: DeploymentCommentSettings) =>
            Invalid(CommentValidationError("Comment is required."))
          case None =>
            Valid(Some(DeploymentComment("")))
        }
      case Some(comment) =>
        settings match {
          case Some(deploymentCommentSettings: DeploymentCommentSettings) =>
            Validated.cond(
              comment.matches(deploymentCommentSettings.validationPattern),
              Some(DeploymentComment(comment)),
              CommentValidationError(comment, deploymentCommentSettings))
          case None => Valid(Some(DeploymentComment(comment)))
        }
    }
  }

  def unsafe(comment: String): DeploymentComment = DeploymentComment(comment)

}

case class CommentValidationError(message: String) extends Exception(message)

object CommentValidationError {
  def apply(comment: String, deploymentCommentSettings: DeploymentCommentSettings) =
    new CommentValidationError(s"Bad comment format '$comment'. Example comment: ${deploymentCommentSettings.exampleComment}.")
}

case class DeploymentCommentSettings(validationPattern: String, exampleComment: String)

object DeploymentCommentSettings {
  def create(validationPattern: String, exampleComment: String): Validated[EmptyDeploymentCommentSettingsError, DeploymentCommentSettings] = {
    Validated.cond(validationPattern.nonEmpty,
      new DeploymentCommentSettings(validationPattern, exampleComment),
      EmptyDeploymentCommentSettingsError("Field validationPattern cannot be empty."))
  }

  def unsafe(validationPattern: String, exampleComment: String): DeploymentCommentSettings = {
    new DeploymentCommentSettings(validationPattern, exampleComment)
  }
}

case class EmptyDeploymentCommentSettingsError(message: String) extends Exception(message)

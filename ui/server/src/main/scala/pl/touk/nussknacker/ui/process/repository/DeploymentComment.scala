package pl.touk.nussknacker.ui.process.repository

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.ui.listener.Comment

class DeploymentComment private(comment: String) extends Comment {

  def withPrefix(prefix: String): DeploymentComment = new DeploymentComment(prefix + value)

  override def value: String = comment
}

object DeploymentComment {

  override def equals(obj: Any): Boolean = super.equals(obj)

  override def hashCode(): Int = super.hashCode()

  def apply(comment: String): DeploymentComment = new DeploymentComment(comment)

  def unapply(deploymentComment: DeploymentComment): String = deploymentComment.value

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

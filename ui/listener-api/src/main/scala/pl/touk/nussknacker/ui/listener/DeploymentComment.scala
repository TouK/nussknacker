package pl.touk.nussknacker.ui.listener

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}

import scala.language.implicitConversions

trait Comment {
  def value: String
}

case class DeploymentComment private(value: String) extends Comment {

  def withPrefix(prefix: String): DeploymentComment = copy(prefix + value)
}

object DeploymentComment {

  def apply(comment: String, settings: Option[DeploySettings]): Validated[CommentValidationError, DeploymentComment] = {

    settings match {
      case Some(deploySettings: DeploySettings) =>
        Validated.cond(
          comment.matches(deploySettings.validationPattern),
          new DeploymentComment(comment),
          CommentValidationError(comment, deploySettings))
      case None => Valid(new DeploymentComment(comment))
    }
  }

  def validateDeploymentComment(comment: Option[String], settings: Option[DeploySettings]): Validated[CommentValidationError, Option[DeploymentComment]] = {
    comment.filterNot(_.isEmpty) match {
      case None if settings.exists(_.validationPattern.nonEmpty) =>
        Invalid(CommentValidationError("Comment is required."))
      case Some(comment) =>
        DeploymentComment(comment, settings).map(Some(_))
      case _ => Valid(None)
    }
  }

  def unsafe(comment: String): DeploymentComment = new DeploymentComment(comment)

}

case class CommentValidationError(message: String) extends Exception(message)

object CommentValidationError {
  def apply(comment: String, deploySettings: DeploySettings) =
    new CommentValidationError(s"Bad comment format '$comment'. Example comment: ${deploySettings.exampleComment}.")
}

case class DeploySettings(validationPattern: String, exampleComment: String)

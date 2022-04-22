package pl.touk.nussknacker.ui.listener

import cats.data.Validated
import cats.data.Validated.Valid
import io.circe.generic.JsonCodec


import scala.language.implicitConversions

trait Comment {
  def value: String
}

case class DeploymentComment(value: String) extends Comment {

  def deployedDeploymentComment: DeploymentComment = withPrefix("Deployment: ")

  def canceledDeploymentComment: DeploymentComment = withPrefix("Stop: ")

  private def withPrefix(prefix: String): DeploymentComment = copy(prefix + value)
}

object DeploymentComment {

  val FinishedDeploymentComment = new DeploymentComment("Scenario finished")

  def apply(comment: String, settings: Option[DeploySettings]): Validated[CommentValidationError, DeploymentComment] = {

    settings match {
      case Some(deploySettings: DeploySettings) =>
        val value = Validated.cond(
          comment.matches(deploySettings.validationPattern),
          new DeploymentComment(comment),
          new CommentValidationError(comment, deploySettings))
        value
      case None => Valid(new DeploymentComment(comment))
    }
  }

  def unsafe(comment: String): DeploymentComment = new DeploymentComment(comment)

}

class CommentValidationError(comment: String, deploySettings: DeploySettings) extends
  Exception(s"Bad comment format '$comment'. Example comment: ${deploySettings.exampleComment}.")

case class DeploySettings(validationPattern: String, exampleComment: String)

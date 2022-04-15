package pl.touk.nussknacker.ui.api

import cats.data.Validated
import cats.data.Validated.Valid
import pl.touk.nussknacker.ui._
import pl.touk.nussknacker.ui.api.DeploymentComment.{PrefixCanceledDeploymentComment, PrefixDeployedDeploymentComment}

import scala.language.implicitConversions

trait Comment {
  def value: String
}

case class DeploymentComment private(value: String) extends Comment {

  def deployedDeploymentComment: DeploymentComment = withPrefix(PrefixDeployedDeploymentComment)

  def canceledDeploymentComment: DeploymentComment = withPrefix(PrefixCanceledDeploymentComment)

  private def withPrefix(prefix: String): DeploymentComment = copy(prefix + value)
}

object DeploymentComment {

  val PrefixDeployedDeploymentComment = "Deploy: "
  val PrefixCanceledDeploymentComment = "Stop: "

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
  Exception(s"Bad comment format '$comment'. Example comment: ${deploySettings.exampleComment}.") with EspError

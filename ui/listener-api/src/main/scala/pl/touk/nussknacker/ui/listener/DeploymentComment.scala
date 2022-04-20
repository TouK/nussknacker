package pl.touk.nussknacker.ui.listener

import scala.language.implicitConversions

trait Comment {
  def value: String
}

case class DeploymentComment(value: String) extends Comment {

  def deployedDeploymentComment: DeploymentComment = withPrefix("Deployment: ")

  def canceledDeploymentComment: DeploymentComment = withPrefix("Stop: ")

  private def withPrefix(prefix: String): DeploymentComment = copy(prefix + value)
}


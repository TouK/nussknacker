package pl.touk.nussknacker.ui.listener

trait Comment {
  def value: String
}

case class DeploymentComment(value: String) extends Comment {

  def withPrefix(prefix: String): DeploymentComment = copy(prefix + value)
}

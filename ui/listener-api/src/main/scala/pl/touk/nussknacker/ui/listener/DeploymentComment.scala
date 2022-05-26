package pl.touk.nussknacker.ui.listener

import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

@ConfiguredJsonCodec
sealed trait Comment {
  def value: String
}

object Comment {
  implicit val configuration: Configuration = Configuration
    .default
    .withDefaults
    .withDiscriminator("type")
}

class InternalComment(val comment: String) extends Comment {
  override def value: String = comment
}

case class DeploymentComment(value: String) extends Comment {

  def withPrefix(prefix: String): DeploymentComment = copy(prefix + value)
}

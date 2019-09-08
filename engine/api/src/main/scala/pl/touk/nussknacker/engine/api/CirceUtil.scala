package pl.touk.nussknacker.engine.api

import io.circe.generic.extras.Configuration

object CirceUtil {

  implicit val configuration: Configuration = Configuration.default.withDiscriminator("type")

}

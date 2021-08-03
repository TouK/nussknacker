package pl.touk.nussknacker.ui.security.api

import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

@ConfiguredJsonCodec
sealed trait FrontendStrategySettings

object FrontendStrategySettings {
  implicit val configuration: Configuration =
    Configuration.default.withDiscriminator("strategy")

  case object Browser extends FrontendStrategySettings

  case class OAuth2(authorizeUrl: Option[String],
                    jwtAuthServerPublicKey: Option[String],
                    jwtIdTokenNonceVerificationRequired: Boolean,
                    implicitGrantEnabled: Boolean) extends FrontendStrategySettings

  case class Remote(moduleUrl: String) extends FrontendStrategySettings
}
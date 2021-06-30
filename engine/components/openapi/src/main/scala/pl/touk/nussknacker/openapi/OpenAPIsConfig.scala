package pl.touk.nussknacker.openapi

import java.net.URL

import net.ceedubs.ficus.readers.ValueReader

case class OpenAPIsConfig(openapis: Option[List[OpenAPIServicesConfig]])

case class OpenAPIServicesConfig(url: URL, rootUrl: Option[URL], securities: Option[Map[String, OpenAPISecurityConfig]])

sealed trait OpenAPISecurityConfig

case class ApiKeyConfig(apiKeyValue: String) extends OpenAPISecurityConfig

object OpenAPIsConfig {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  implicit val openAPIsConfigVR: ValueReader[OpenAPIsConfig] = ValueReader.relative(conf => {
    OpenAPIsConfig(
      openapis = conf.as[Option[List[OpenAPIServicesConfig]]]("openapi")
    )
  })

  implicit val openAPIServicesConfigVR: ValueReader[OpenAPIServicesConfig] = ValueReader.relative(conf => {
    OpenAPIServicesConfig(
      url = conf.as[URL]("url"),
      rootUrl = conf.as[Option[URL]]("rootUrl"),
      securities = conf.as[Option[Map[String, OpenAPISecurityConfig]]]("security")
    )
  })

  implicit val openAPISecurityConfigVR: ValueReader[OpenAPISecurityConfig] = ValueReader.relative(conf => {
    conf.as[String]("type") match {
      case "apiKey" => conf.rootAs[ApiKeyConfig]
      case typ => throw new Exception(s"Not supported swagger security type '$typ' in the configuration")
    }
  })

  implicit val apiKeyConfigVR: ValueReader[ApiKeyConfig] = ValueReader.relative(conf => {
    ApiKeyConfig(
      apiKeyValue = conf.as[String]("apiKeyValue")
    )
  })
}
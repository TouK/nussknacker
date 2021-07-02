package pl.touk.nussknacker.openapi

import io.swagger.v3.oas.models.PathItem.HttpMethod

import java.net.URL
import net.ceedubs.ficus.readers.ValueReader

import scala.util.matching.Regex

case class OpenAPIServicesConfig(//by default we allow only GET, as enrichers should be idempotent and not change data
                                 allowedMethods: List[String] = List(HttpMethod.GET.name()),
                                 namePattern: Regex = ".*".r,
                                 rootUrl: Option[URL] = None,
                                 securities: Option[Map[String, OpenAPISecurityConfig]] = None)

sealed trait OpenAPISecurityConfig

case class ApiKeyConfig(apiKeyValue: String) extends OpenAPISecurityConfig

object OpenAPIsConfig {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._


  implicit val openAPIServicesConfigVR: ValueReader[OpenAPIServicesConfig] = ValueReader.relative(conf => {
    OpenAPIServicesConfig(
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
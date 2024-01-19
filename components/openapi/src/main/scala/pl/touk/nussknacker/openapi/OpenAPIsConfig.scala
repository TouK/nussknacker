package pl.touk.nussknacker.openapi

import com.typesafe.config.Config
import io.swagger.v3.oas.models.PathItem.HttpMethod
import net.ceedubs.ficus.readers.{ArbitraryTypeReader, ValueReader}
import pl.touk.nussknacker.http.backend.{DefaultHttpClientConfig, HttpClientConfig}
import sttp.model.StatusCode

import java.net.URL
import scala.util.matching.Regex

final case class OpenAPIServicesConfig(
    url: URL,
    // by default we allow only GET, as enrichers should be idempotent and not change data
    allowedMethods: List[String] = List(HttpMethod.GET.name()),
    codesToInterpretAsEmpty: List[Int] = List(StatusCode.NotFound.code),
    namePattern: Regex = ".*".r,
    rootUrl: Option[URL] = None,
    security: Option[Map[String, OpenAPISecurityConfig]] = None,
    httpClientConfig: HttpClientConfig = DefaultHttpClientConfig()
)

sealed trait OpenAPISecurityConfig

final case class ApiKeyConfig(apiKeyValue: String) extends OpenAPISecurityConfig

object OpenAPIsConfig {

  import net.ceedubs.ficus.Ficus._

  implicit val openAPIServicesConfigVR: ValueReader[OpenAPIServicesConfig] =
    ArbitraryTypeReader.arbitraryTypeValueReader[OpenAPIServicesConfig]

  implicit val regexReader: ValueReader[Regex] = (config: Config, path: String) => new Regex(config.getString(path))

  implicit val openAPISecurityConfigVR: ValueReader[OpenAPISecurityConfig] = ValueReader.relative(conf => {
    conf.as[String]("type") match {
      case "apiKey" => conf.as[ApiKeyConfig]
      case typ      => throw new Exception(s"Not supported swagger security type '$typ' in the configuration")
    }
  })

  implicit val apiKeyConfigVR: ValueReader[ApiKeyConfig] = ValueReader.relative(conf => {
    ApiKeyConfig(
      apiKeyValue = conf.as[String]("apiKeyValue")
    )
  })

}

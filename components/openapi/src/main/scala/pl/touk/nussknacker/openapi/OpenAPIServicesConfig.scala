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
    // For backward compatibility it is called security. We should probably rename it and bundle together with secret
    private val security: Map[SecuritySchemeName, Secret] = Map.empty,
    private val secret: Option[Secret] = None,
    httpClientConfig: HttpClientConfig = DefaultHttpClientConfig()
) {
  def securityConfig: SecurityConfig =
    new SecurityConfig(secretBySchemeName = security, commonSecretForAnyScheme = secret)
}

final class SecurityConfig(
    secretBySchemeName: Map[SecuritySchemeName, Secret],
    commonSecretForAnyScheme: Option[Secret]
) {

  def secret(schemeName: SecuritySchemeName): Option[Secret] =
    secretBySchemeName.get(schemeName) orElse commonSecretForAnyScheme

}

object SecurityConfig {
  def empty: SecurityConfig = new SecurityConfig(Map.empty, None)
}

final case class SecuritySchemeName(value: String)

sealed trait Secret

final case class ApiKeySecret(apiKeyValue: String) extends Secret

object OpenAPIServicesConfig {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  import HttpClientConfig._

  implicit val securitySchemeNameVR: ValueReader[SecuritySchemeName] =
    ValueReader[String].map(SecuritySchemeName(_))

  implicit val regexReader: ValueReader[Regex] = (config: Config, path: String) => new Regex(config.getString(path))

  implicit val apiKeyVR: ValueReader[ApiKeySecret] = ValueReader.relative { conf =>
    ApiKeySecret(
      apiKeyValue = conf.as[String]("apiKeyValue")
    )
  }

  implicit val secretVR: ValueReader[Secret] = ValueReader.relative { conf =>
    conf.as[String]("type") match {
      case "apiKey" => conf.rootAs[ApiKeySecret]
      case typ      => throw new Exception(s"Not supported swagger security type '$typ' in the configuration")
    }
  }

  implicit val secretBySchemeNameVR: ValueReader[Map[SecuritySchemeName, Secret]] =
    ValueReader[Map[String, Secret]].map { secretBySchemeName =>
      secretBySchemeName.map { case (schemeNameString, secret) =>
        SecuritySchemeName(schemeNameString) -> secret
      }
    }

  implicit val openAPIServicesConfigVR: ValueReader[OpenAPIServicesConfig] =
    ArbitraryTypeReader.arbitraryTypeValueReader[OpenAPIServicesConfig]

}

package pl.touk.nussknacker.http

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.http.backend.{DefaultHttpClientConfig, HttpClientConfig}
import pl.touk.nussknacker.http.enricher.HttpEnricher.ApiKeyConfig.{ApiKeyInCookie, ApiKeyInHeader, ApiKeyInQuery}
import pl.touk.nussknacker.http.enricher.HttpEnricher.HttpMethod.{DELETE, GET, POST, PUT}
import pl.touk.nussknacker.http.enricher.HttpEnricher.{ApiKeyConfig, HttpMethod}

import java.net.URL

final case class HttpEnricherConfig(
    rootUrl: Option[URL],
    security: Option[List[ApiKeyConfig]],
    httpClientConfig: HttpClientConfig,
    allowedMethods: List[HttpMethod]
)

object HttpEnricherConfig {
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig

  implicit val apiKeyReader: ValueReader[ApiKeyConfig] = ValueReader.relative(conf => {
    val name  = conf.as[String]("name")
    val value = conf.as[String]("value")
    conf.as[String]("in") match {
      case "query"  => ApiKeyInQuery(name, value)
      case "header" => ApiKeyInHeader(name, value)
      case "cookie" => ApiKeyInCookie(name, value)
    }
  })

  implicit val httpMethodReader: ValueReader[HttpMethod] = ValueReader.relative(conf => {
    import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
    conf.rootAs[HttpMethod]
  })

  implicit val configValueReader: ValueReader[HttpEnricherConfig] = ValueReader.relative(conf => {
    HttpEnricherConfig(
      // TODO decision: add '/' in reader if not present? or during evaluation?
      rootUrl = optionValueReader[URL].read(conf, "rootUrl").map { url =>
        if (url.getQuery != null) {
          throw new IllegalArgumentException("Root URL for HTTP enricher has to be without query parameters.")
        } else {
          url
        }
      },
      security = optionValueReader[List[ApiKeyConfig]].read(conf, "security"),
      httpClientConfig =
        optionValueReader[HttpClientConfig].read(conf, "httpClientConfig").getOrElse(DefaultHttpClientConfig()),
      allowedMethods = {
        val methods = optionValueReader[List[HttpMethod]].read(conf, "allowedMethods").getOrElse(DefaultAllowedMethods)
        if (methods.isEmpty) {
          throw new IllegalArgumentException("Allowed methods cannot be empty.")
        }
        methods
      }
    )
  })

  private val DefaultAllowedMethods = List(GET, POST, PUT, DELETE)

  private[http] def parse(config: Config) = config.rootAs[HttpEnricherConfig]
}

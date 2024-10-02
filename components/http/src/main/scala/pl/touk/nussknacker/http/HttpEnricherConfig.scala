package pl.touk.nussknacker.http

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.http.HttpEnricherConfig.ApiKeyConfig
import pl.touk.nussknacker.http.backend.{DefaultHttpClientConfig, HttpClientConfig}

import java.net.URL

final case class HttpEnricherConfig(
    rootUrl: Option[URL],
    security: Option[List[ApiKeyConfig]],
    httpClientConfig: HttpClientConfig
)

object HttpEnricherConfig {

  sealed trait ApiKeyConfig {
    val name: String
    val value: String
  }

  final case class ApiKeyInQuery(name: String, value: String)  extends ApiKeyConfig
  final case class ApiKeyInHeader(name: String, value: String) extends ApiKeyConfig
  final case class ApiKeyInCookie(name: String, value: String) extends ApiKeyConfig

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

  implicit val configValueReader: ValueReader[HttpEnricherConfig] = ValueReader.relative(conf => {
    HttpEnricherConfig(
      //    TODO: add '/' in reader if not present? or during evaluation?
      rootUrl = optionValueReader[URL].read(conf, "rootUrl").map { url =>
        if (url.getQuery != null) {
          throw new IllegalArgumentException("Root URL for HTTP enricher has to be without query parameters.")
        } else {
          url
        }
      },
      security = optionValueReader[List[ApiKeyConfig]].read(conf, "security"),
      httpClientConfig =
        optionValueReader[HttpClientConfig].read(conf, "httpClientConfig").getOrElse(DefaultHttpClientConfig())
    )
  })

  private[http] def parse(config: Config) = config.rootAs[HttpEnricherConfig]
}

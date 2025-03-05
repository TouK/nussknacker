package pl.touk.nussknacker.openapi.discovery

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.asynchttpclient.DefaultAsyncHttpClient
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.http.backend.HttpClientConfig
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import pl.touk.nussknacker.openapi.parser.{ServiceParseError, SwaggerParser}
import sttp.client3.{basicRequest, SttpBackend}
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.Uri

import java.io.File
import java.nio.charset.StandardCharsets
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

object SwaggerOpenApiDefinitionDiscovery
    extends SwaggerOpenApiDefinitionDiscovery()(
      AsyncHttpClientFutureBackend.usingClient(
        new DefaultAsyncHttpClient(
          HttpClientConfig(
            timeout = Some(5 seconds),
            connectTimeout = Some(10 seconds),
            maxPoolSize = Some(1),
            None,
            None,
            None,
            None
          ).toAsyncHttpClientConfig(None).build()
        )
      )
    )

class SwaggerOpenApiDefinitionDiscovery(implicit val httpBackend: SttpBackend[Future, Any]) extends LazyLogging {

  def discoverOpenAPIServices(
      openAPIsConfig: OpenAPIServicesConfig
  ): List[Validated[ServiceParseError, SwaggerService]] = {
    val discoveryUrl = openAPIsConfig.url
    val definition = if (discoveryUrl.getProtocol == "file") {
      ResourceLoader.load(new File(discoveryUrl.getPath))
    } else {
      Await
        .result(basicRequest.get(Uri(discoveryUrl.toURI)).send(httpBackend), 20 seconds)
        .body
        .fold(left => throw new IllegalStateException(s"Invalid response from discovery API: $left"), identity)
    }
    SwaggerParser.parse(definition, openAPIsConfig)
  }

}

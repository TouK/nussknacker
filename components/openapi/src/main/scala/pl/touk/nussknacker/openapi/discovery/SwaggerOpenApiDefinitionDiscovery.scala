package pl.touk.nussknacker.openapi.discovery

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.asynchttpclient.DefaultAsyncHttpClient
import pl.touk.nussknacker.openapi.http.backend.HttpClientConfig
import pl.touk.nussknacker.openapi.parser.{ServiceParseError, SwaggerParser}
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{SttpBackend, basicRequest}
import sttp.model.Uri

import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

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

class SwaggerOpenApiDefinitionDiscovery(implicit val httpBackend: SttpBackend[Future, Nothing, Nothing])
    extends LazyLogging {

  def discoverOpenAPIServices(
    discoveryUrl: URL,
    openAPIsConfig: OpenAPIServicesConfig
  ): List[Validated[ServiceParseError, SwaggerService]] = {
    val definition = if (discoveryUrl.getProtocol == "file") {
      FileUtils.readFileToString(new File(discoveryUrl.getPath), StandardCharsets.UTF_8)
    } else {
      Await
        .result(basicRequest.get(Uri(discoveryUrl.toURI)).send(), 20 seconds)
        .body
        .fold(left => throw new IllegalStateException(s"Invalid response from discovery API: $left"), identity)
    }
    SwaggerParser.parse(definition, openAPIsConfig)
  }

}

package pl.touk.nussknacker.openapi.discovery

import com.typesafe.config.{ConfigFactory, ConfigObject}
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import org.asynchttpclient.DefaultAsyncHttpClient
import pl.touk.nussknacker.openapi.OpenAPIServicesConfig
import pl.touk.nussknacker.openapi.http.backend.HttpClientConfig
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{SttpBackend, basicRequest}
import sttp.model.Uri

import java.net.URL
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object SwaggerOpenApiDefinitionDiscovery extends SwaggerOpenApiDefinitionDiscovery()(
  AsyncHttpClientFutureBackend.usingClient(new DefaultAsyncHttpClient(HttpClientConfig(
    timeout = Some(5 seconds),
    connectTimeout = Some(10 seconds),
    maxPoolSize = Some(1),
    None, None, None, None
  ).toAsyncHttpClientConfig(None).build())))

class SwaggerOpenApiDefinitionDiscovery(implicit val httpBackend: SttpBackend[Future, Nothing, Nothing]) extends LazyLogging {

  def discoverOpenAPIServices(discoveryUrl: URL, openAPIsConfig: OpenAPIServicesConfig): List[ConfigObject] = {
    val definition = Await.result(basicRequest.get(Uri(discoveryUrl.toURI)).send(), 20 seconds).body.fold(
      left => throw new IllegalStateException(s"Invalid response from discovery API: $left"), identity)
    val services = SwaggerParser.parse(definition, openAPIsConfig)

    logger.info(s"Discovered OpenAPI: ${services.map(_.name)}")

    services.map(service => ConfigFactory.parseString(service.asJson.spaces2).root())
  }

}

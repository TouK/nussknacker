package pl.touk.nussknacker.openapi.enrichers

import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.standalone.utils.service
import pl.touk.nussknacker.openapi.SwaggerService
import pl.touk.nussknacker.openapi.http.backend.{HttpClientConfig, SharedHttpClient, SharedHttpClientHolder}
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class FlinkSwaggerEnricher(rootUrl: Option[URL], swaggerService: SwaggerService,
                           fixedParams: Map[String, () => AnyRef],
                           httpClientConfig: HttpClientConfig) extends BaseSwaggerEnricher(rootUrl, swaggerService, fixedParams) with service.TimeMeasuringService {

  private var httpClient: SharedHttpClient = _

  override def open(jobData: JobData): Unit = {
    httpClient = SharedHttpClientHolder.retrieveService(httpClientConfig)(jobData.metaData)
  }

  override def close(): Unit = synchronized {
    Option(httpClient).foreach(_.close())
  }

  override implicit protected def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing] =
    AsyncHttpClientFutureBackend.usingClient(httpClient.httpClient)
}

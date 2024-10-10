package pl.touk.nussknacker.http.client

import com.typesafe.scalalogging.LazyLogging
import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient}
import org.slf4j.Logger
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.sharedservice.{SharedService, SharedServiceHolder}
import pl.touk.nussknacker.http.backend.{HttpBackendProvider, HttpClientConfig}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.logging.slf4j.Slf4jLoggingBackend

import scala.concurrent.{ExecutionContext, Future}

// Copied from OpenAPI enricher - TODO: extract to utils to share with OpenAPI
private[client] class SharedHttpClientBackendProvider(httpClientConfig: HttpClientConfig)
    extends HttpBackendProvider
    with LazyLogging {

  private var httpClient: SharedHttpClient = _

  override def open(context: EngineRuntimeContext): Unit = {
    httpClient = SharedHttpClientBackendProvider.retrieveService(httpClientConfig)(context.jobData.metaData)
  }

  override def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Any] =
    AsyncHttpClientFutureBackend.usingClient(httpClient.httpClient)

  override def close(): Unit = Option(httpClient).foreach(_.close())

}

private[client] object SharedHttpClientBackendProvider extends SharedServiceHolder[HttpClientConfig, SharedHttpClient] {

  override protected def createService(config: HttpClientConfig, metaData: MetaData): SharedHttpClient = {
    val httpClientConfig = config.toAsyncHttpClientConfig(Option(metaData.name))
    new SharedHttpClient(new DefaultAsyncHttpClient(httpClientConfig.build()), config)
  }

}

private[client] class SharedHttpClient(val httpClient: AsyncHttpClient, config: HttpClientConfig)
    extends SharedService[HttpClientConfig] {

  override def creationData: HttpClientConfig = config

  override protected def sharedServiceHolder: SharedHttpClientBackendProvider.type = SharedHttpClientBackendProvider

  override def internalClose(): Unit = httpClient.close()
}

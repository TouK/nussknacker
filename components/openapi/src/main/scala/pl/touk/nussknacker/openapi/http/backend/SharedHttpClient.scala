package pl.touk.nussknacker.openapi.http.backend

import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient}
import pl.touk.nussknacker.engine.api.{CustomMetaData, MetaData}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.sharedservice.{SharedService, SharedServiceHolder}
import pl.touk.nussknacker.http.backend.{HttpBackendProvider, HttpClientConfig}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.{ExecutionContext, Future}

class SharedHttpClientBackendProvider(httpClientConfig: HttpClientConfig) extends HttpBackendProvider {

  private var basicHttpClient: SharedHttpClient        = _
  private var serviceAwareHttpClient: SharedHttpClient = _

  override def open(context: EngineRuntimeContext): Unit = {
    serviceAwareHttpClient = SharedHttpClientBackendProvider.retrieveService(httpClientConfig)(context.jobData.metaData)
  }

  override def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Any] = {
    val httpClient = if (serviceAwareHttpClient == null) {
      synchronized {
        if (basicHttpClient == null) {
          basicHttpClient = SharedHttpClientBackendProvider.retrieveService(httpClientConfig)(
            MetaData("forEc", CustomMetaData(Map.empty))
          )
        }
      }
      basicHttpClient
    } else {
      serviceAwareHttpClient
    }
    AsyncHttpClientFutureBackend.usingClient(httpClient.httpClient)
  }

  override def close(): Unit = {
    Option(basicHttpClient).foreach(_.close())
    Option(serviceAwareHttpClient).foreach(_.close())
  }

}

object SharedHttpClientBackendProvider extends SharedServiceHolder[HttpClientConfig, SharedHttpClient] {

  override protected def createService(config: HttpClientConfig, metaData: MetaData): SharedHttpClient = {
    val httpClientConfig = config.toAsyncHttpClientConfig(Option(metaData.name))
    new SharedHttpClient(new DefaultAsyncHttpClient(httpClientConfig.build()), config)
  }

}

class SharedHttpClient(val httpClient: AsyncHttpClient, config: HttpClientConfig)
    extends SharedService[HttpClientConfig] {

  override def creationData: HttpClientConfig = config

  override protected def sharedServiceHolder: SharedHttpClientBackendProvider.type = SharedHttpClientBackendProvider

  override def internalClose(): Unit = httpClient.close()
}

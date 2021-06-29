package pl.touk.nussknacker.openapi.http.backend

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.flink.util.sharedservice.{SharedService, SharedServiceHolder}
import pl.touk.nussknacker.openapi.http.backend.OpenapiSttpBackend.HttpBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.ExecutionContext

object CacheableHttpBackendHolder extends SharedServiceHolder[(HttpClientConfig, ExecutionContext), ShutdownableHttpClient] {

  override protected def createService(config: (HttpClientConfig, ExecutionContext), metaData: MetaData): ShutdownableHttpClient = {
    val httpClientConfig = config._1.toAsyncHttpClientConfig(Option(metaData.id))
    val backend = AsyncHttpClientFutureBackend.usingConfigBuilder(_ => httpClientConfig)(config._2)
    new ShutdownableHttpClient(backend, config)
  }

}


class ShutdownableHttpClient(val httpBackend: HttpBackend, config: (HttpClientConfig, ExecutionContext)) extends SharedService[(HttpClientConfig, ExecutionContext)] {

  override def creationData: (HttpClientConfig, ExecutionContext) = config

  override protected def sharedServiceHolder: CacheableHttpBackendHolder.type = CacheableHttpBackendHolder

  override def internalClose(): Unit = httpBackend.close()
}

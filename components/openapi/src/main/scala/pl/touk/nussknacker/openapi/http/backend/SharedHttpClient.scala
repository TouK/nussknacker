package pl.touk.nussknacker.openapi.http.backend

import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{JobData, MetaData}
import pl.touk.nussknacker.engine.flink.util.sharedservice.{SharedService, SharedServiceHolder}
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.{ExecutionContext, Future}

class SharedHttpClientBackendProvider(httpClientConfig: HttpClientConfig) extends HttpBackendProvider {

  private var httpClient: SharedHttpClient = _

  override def open(context: EngineRuntimeContext): Unit = {
    httpClient = SharedHttpClientBackendProvider.retrieveService(httpClientConfig)(context.jobData.metaData)
  }

  override def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing] =
    AsyncHttpClientFutureBackend.usingClient(httpClient.httpClient)

  override def close(): Unit = Option(httpClient).foreach(_.close())

}

object SharedHttpClientBackendProvider extends SharedServiceHolder[HttpClientConfig, SharedHttpClient] {

  override protected def createService(config: HttpClientConfig, metaData: MetaData): SharedHttpClient = {
    val httpClientConfig = config.toAsyncHttpClientConfig(Option(metaData.id))
    new SharedHttpClient(new DefaultAsyncHttpClient(httpClientConfig.build()), config)
  }

}


class SharedHttpClient(val httpClient: AsyncHttpClient, config: HttpClientConfig) extends SharedService[HttpClientConfig] {

  override def creationData: HttpClientConfig = config

  override protected def sharedServiceHolder: SharedHttpClientBackendProvider.type = SharedHttpClientBackendProvider

  override def internalClose(): Unit = httpClient.close()
}

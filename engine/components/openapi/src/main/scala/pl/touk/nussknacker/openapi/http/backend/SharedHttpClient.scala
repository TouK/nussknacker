package pl.touk.nussknacker.openapi.http.backend

import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.flink.util.sharedservice.{SharedService, SharedServiceHolder}

object SharedHttpClientHolder extends SharedServiceHolder[HttpClientConfig, SharedHttpClient] {

  override protected def createService(config: HttpClientConfig, metaData: MetaData): SharedHttpClient = {
    val httpClientConfig = config.toAsyncHttpClientConfig(Option(metaData.id))
    new SharedHttpClient(new DefaultAsyncHttpClient(httpClientConfig.build()), config)
  }

}


class SharedHttpClient(val httpClient: AsyncHttpClient, config: HttpClientConfig) extends SharedService[HttpClientConfig] {

  override def creationData: HttpClientConfig = config

  override protected def sharedServiceHolder: SharedHttpClientHolder.type = SharedHttpClientHolder

  override def internalClose(): Unit = httpClient.close()
}

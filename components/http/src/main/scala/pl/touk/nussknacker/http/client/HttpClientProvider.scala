package pl.touk.nussknacker.http.client

import org.asynchttpclient.DefaultAsyncHttpClient
import pl.touk.nussknacker.http.backend.{FixedAsyncHttpClientBackendProvider, HttpBackendProvider, HttpClientConfig}

import scala.util.Try

// Copied from OpenAPI enricher - TODO: extract to utils to share with OpenAPI
object HttpClientProvider {

  def getBackendProvider(httpClientConfig: HttpClientConfig): HttpBackendProvider = {
    val isFlinkBased = Try(
      getClass.getClassLoader
        .loadClass("org.apache.flink.streaming.api.environment.StreamExecutionEnvironment")
    ).isSuccess
    if (isFlinkBased) {
      new SharedHttpClientBackendProvider(httpClientConfig)
    } else {
      // TODO: figure out how to create client only once and enable its closing. Also: do we want to pass processId here?
      // Should client be one per engine deployment, or per scenario?
      val httpClient = new DefaultAsyncHttpClient(httpClientConfig.toAsyncHttpClientConfig(None).build())
      new FixedAsyncHttpClientBackendProvider(httpClient)
    }
  }

}

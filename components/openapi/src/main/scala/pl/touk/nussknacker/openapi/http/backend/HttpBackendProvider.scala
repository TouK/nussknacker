package pl.touk.nussknacker.openapi.http.backend

import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContextLifecycle
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.{ExecutionContext, Future}

trait HttpBackendProvider extends EngineRuntimeContextLifecycle with AutoCloseable {

  def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing]

  def close(): Unit = {}
}

class FixedAsyncHttpClientBackendProvider(httpClient: AsyncHttpClient) extends HttpBackendProvider {

  override def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing] =
    AsyncHttpClientFutureBackend.usingClient(httpClient)

}
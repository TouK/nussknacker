package pl.touk.nussknacker.openapi.http.backend

import org.asynchttpclient.AsyncHttpClient
import pl.touk.nussknacker.engine.api.Lifecycle
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.{ExecutionContext, Future}

trait HttpBackendProvider extends Lifecycle {

  def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing]

}

class FixedAsyncHttpClientBackendProvider(httpClient: AsyncHttpClient) extends HttpBackendProvider {

  override def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing] =
    AsyncHttpClientFutureBackend.usingClient(httpClient)

}
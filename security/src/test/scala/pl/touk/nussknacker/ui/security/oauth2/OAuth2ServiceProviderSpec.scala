package pl.touk.nussknacker.ui.security.oauth2

import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class OAuth2ServiceProviderSpec extends FlatSpec with Matchers {
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
  it should "return default OAuth2 service" in {
    OAuth2ServiceProvider(ExampleOAuth2ServiceFactory.testConfig, this.getClass.getClassLoader) shouldBe a[OAuth2Service[_, _]]
  }
}

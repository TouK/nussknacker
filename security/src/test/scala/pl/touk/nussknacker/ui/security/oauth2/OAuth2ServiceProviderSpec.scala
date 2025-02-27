package pl.touk.nussknacker.ui.security.oauth2

import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OAuth2ServiceProviderSpec extends AnyFlatSpec with Matchers {
  implicit val backend: SttpBackend[Future, Any] =
    AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

  it should "return default OAuth2 service" in {
    OAuth2ServiceProvider(ExampleOAuth2ServiceFactory.testConfig, this.getClass.getClassLoader) shouldBe a[
      OAuth2Service[_, _]
    ]
  }

}

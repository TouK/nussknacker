package pl.touk.nussknacker.ui.security.api.ouath2

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.ui.security.api.oauth2.{DefaultOAuth2Service, OAuth2ServiceProvider}

class OAuth2ServiceProviderSpec extends FlatSpec with Matchers {
  it should "return default OAuth2 service" in {
    OAuth2ServiceProvider(ExampleOAuth2ServiceFactory.testConfig, this.getClass.getClassLoader, List.empty) shouldBe a[DefaultOAuth2Service]
  }
}

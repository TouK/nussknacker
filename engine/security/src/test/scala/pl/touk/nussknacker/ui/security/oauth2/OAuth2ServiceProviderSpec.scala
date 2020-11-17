package pl.touk.nussknacker.ui.security.oauth2

import org.scalatest.{FlatSpec, Matchers}

class OAuth2ServiceProviderSpec extends FlatSpec with Matchers {
  it should "return default OAuth2 service" in {
    OAuth2ServiceProvider(ExampleOAuth2ServiceFactory.testConfig, this.getClass.getClassLoader, List.empty) shouldBe a[OAuth2Service]
  }
}

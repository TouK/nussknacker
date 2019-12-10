package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class AuthenticationConfigurationSpec extends FlatSpec with Matchers with ScalatestRouteTest {
  it should "parse rules default config" in {

    val config = ConfigFactory.parseString(
      """
        authentication: {
         usersFile: "./src/test/resources/oauth2-users.conf"
        }
      """.stripMargin)

    val authConfig = DefaultAuthenticationConfiguration.create(config)
    authConfig shouldBe a[DefaultAuthenticationConfiguration]
    authConfig.method shouldBe AuthenticationMethod.Other
  }
}

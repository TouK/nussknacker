package pl.touk.nussknacker.ui.security

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthConfiguration
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration

class AuthenticationConfigurationFactorySpec extends FlatSpec with Matchers with ScalatestRouteTest {
  it should "parse rules default config" in {

    val config = ConfigFactory.parseString(
      """
        authentication: {
         usersFile: "./develConf/tests/oauth2-users.conf"
        }
      """.stripMargin)

    val authConfig = AuthenticationConfigurationFactory(config)
    authConfig shouldBe a[DefaultAuthenticationConfiguration]
    authConfig.backend shouldBe AuthenticationBackend.Other
  }

  it should "parse rules oauth2 config" in {
    val config = ConfigFactory.parseString(
      """
        |authentication: {
        |  backend: "OAuth2",
        |  clientSecret: "17c382f1048"
        |  clientId: "1de7"
        |  authorizeUri: "https://github.com/login/oauth/authorize"
        |  redirectUri: "http://localhost:3000"
        |  accessTokenUri: "https://github.com/login/oauth/access_token"
        |  profileUri: "https://api.github.com/user"
        |  usersFile: "./develConf/tests/oauth2-users.conf"
        |}
      """.stripMargin)

    val authConfig = AuthenticationConfigurationFactory(config)
    authConfig shouldBe a[OAuth2Configuration]
    authConfig.backend shouldBe AuthenticationBackend.OAuth2
  }

  it should "raise exception with missing fields at parse rules oauth2 config" in {
    val config = ConfigFactory.parseString(
      """
        authentication: {
          backend: "OAuth2"
          usersFile: "./develConf/tests/oauth2-users.conf"
        }
      """.stripMargin)

    try {
      AuthenticationConfigurationFactory(config)
    } catch {
      case _: ConfigException => succeed
    }
  }

  it should "parse rules basicauth config" in {
    val config = ConfigFactory.parseString(
      """
        |authentication: {
        |  backend: "BasicAuth"
        |  usersFile: "./develConf/tests/oauth2-users.conf"
        |}
      """.stripMargin)

    val authConfig = AuthenticationConfigurationFactory(config)
    authConfig shouldBe a[BasicAuthConfiguration]
    authConfig.backend shouldBe AuthenticationBackend.BasicAuth
  }
}

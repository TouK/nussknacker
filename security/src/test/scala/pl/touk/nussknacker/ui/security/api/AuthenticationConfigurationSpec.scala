package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.engine.util.cache.CacheConfig
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationConfiguration
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration

import scala.concurrent.duration._

class AuthenticationConfigurationSpec extends AnyFlatSpec with Matchers with ScalatestRouteTest with OptionValues {

  it should "parse rules default config" in {

    val config = ConfigFactory.parseString("""
        authentication: {
         usersFile: "./src/test/resources/oauth2-users.conf"
        }
      """.stripMargin)

    val authConfig = BasicAuthenticationConfiguration.create(config)
    authConfig shouldBe a[BasicAuthenticationConfiguration]
    authConfig.name shouldBe BasicAuthenticationConfiguration.name
    authConfig.cachingHashesOrDefault.isEnabled shouldBe false
  }

  it should "parse caching hashes" in {
    val config = ConfigFactory.parseString("""
        authentication: {
         usersFile: "./src/test/resources/oauth2-users.conf"
         cachingHashes {
           enabled: true
           expireAfterAccess: 10m
         }
        }
      """.stripMargin)

    val authConfig = BasicAuthenticationConfiguration.create(config)
    authConfig.cachingHashesOrDefault.isEnabled shouldBe true
    authConfig.cachingHashesOrDefault.toCacheConfig.value shouldEqual CacheConfig(expireAfterAccess = Some(10.minutes))
  }

  it should "parse is admin impersonation possible" in {
    val config = ConfigFactory.parseString("""
        authentication: {
         usersFile: "./src/test/resources/oauth2-users.conf"
         isAdminImpersonationPossible: true
        }
      """.stripMargin)

    val authConfig = BasicAuthenticationConfiguration.create(config)
    authConfig.isAdminImpersonationPossible shouldBe true
  }

  it should "parse oidc config with no users" in {
    val config = ConfigFactory
      .parseString(ResourceLoader.load("/oidc.conf"))
      .withValue(
        "authentication.usersFile",
        fromAnyRef("./src/test/resources/oauth2-no-users.conf")
      )

    val authConfig = OAuth2Configuration.create(config)
    authConfig.users shouldBe List()
  }

}

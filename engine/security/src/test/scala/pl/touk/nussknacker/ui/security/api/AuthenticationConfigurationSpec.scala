package pl.touk.nussknacker.ui.security.api

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.nussknacker.engine.util.cache.CacheConfig
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationConfiguration

import scala.concurrent.duration._

class AuthenticationConfigurationSpec extends FlatSpec with Matchers with ScalatestRouteTest with OptionValues {
  it should "parse rules default config" in {

    val config = ConfigFactory.parseString(
      """
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
    val config = ConfigFactory.parseString(
      """
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
}

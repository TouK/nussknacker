package pl.touk.nussknacker.ui.security.ssl

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers, OptionValues}

class SslConfigParserSpec extends FlatSpec with Matchers with OptionValues {

  it should "parse implicitly disabled ssl configuration" in {
    val config = ConfigFactory.parseString(
      """ssl {
         }
      """.stripMargin)

    SslConfigParser.sslEnabled(config) shouldBe empty
  }

  it should "parse enabled ssl configuration" in {
    val config = ConfigFactory.parseString(
      """ssl {
           enabled: true
           keyStore {
             location: /some/location
             password: foobar
           }
         }
      """.stripMargin)

    val keyStoreConfig = SslConfigParser.sslEnabled(config).value
    new String(keyStoreConfig.password) shouldEqual "foobar"
  }

  it should "parse explicitly disabled ssl configuration" in {
    val config = ConfigFactory.parseString(
      """ssl {
           enabled: false
           keyStore {
             location: /some/location
             password: foobar
           }
         }
      """.stripMargin)

    SslConfigParser.sslEnabled(config) shouldBe empty
  }

}

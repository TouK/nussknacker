package pl.touk.nussknacker.ui.process.processingtype

import cats.effect.IO
import com.typesafe
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, include}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.ui.process.processingtype.loader.LoadableConfigBasedNussknackerConfig

class ProcessingTypeDataConfigurationReaderSpec extends AnyFunSuite {

  test("should throw when required configuration is missing") {
    val configuration = ConfigFactory.parseString(
      """
        |scenarioTypes {
        |  "streaming" {
        |    deploymentConfig {
        |      restUrl: "http://localhost:8081"
        |      typeMissing: "flinkStreaming"
        |    }
        |
        |    modelConfig {
        |      classPath: ["test.jar"]
        |    }
        |
        |    category: "Default"
        |  }
        |}
        |""".stripMargin
    )

    val config = configuration.resolve()

    intercept[typesafe.config.ConfigException] {
      processingTypeDataReader(config).processingTypeConfigs()
    }.getMessage should include("No configuration setting found for key 'deploymentConfig.type'")
  }

  test("should throw when no configuration is provided") {
    val configuration = ConfigFactory.parseString(
      """
        |test {}
        |""".stripMargin
    )

    val config = configuration.resolve()

    intercept[RuntimeException] {
      processingTypeDataReader(config).processingTypeConfigs()
    }.getMessage should include("No scenario types configuration provided")
  }

  private def processingTypeDataReader(config: Config) = {
    new LoadableConfigBasedNussknackerConfig(IO.pure(ConfigWithUnresolvedVersion(config)))
  }

}

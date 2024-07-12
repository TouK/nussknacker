package pl.touk.nussknacker.ui.process.processingtype

import com.typesafe
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, include}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion

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
      ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(ConfigWithUnresolvedVersion(config))
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
      ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(ConfigWithUnresolvedVersion(config))
    }.getMessage should include("No scenario types configuration provided")
  }

}

package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.FunSuite
import org.scalatest.Matchers.{convertToAnyShouldWrapper, include}
import pl.touk.nussknacker.engine.api.config.LoadedConfig

class ProcessingTypeDataConfigurationReaderSpec extends FunSuite {

  private val oldConfiguration: Config = ConfigFactory.parseString(
    """
      |processTypes {
      |  "streaming" {
      |    deploymentConfig {
      |      jobManagerTimeout: 1m
      |      restUrl: "http://localhost:8081"
      |      queryableStateProxyUrlMissing: "localhost:9123"
      |      type: "flinkStreaming"
      |    }
      |
      |    modelConfig {
      |      classPath: ["test.jar"]
      |    }
      |  }
      |}
      |""".stripMargin
  )

  import scala.collection.JavaConverters._

  test("should load old processTypes configuration") {
    val processTypes = ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(LoadedConfig(oldConfiguration, oldConfiguration))

    processTypes.size shouldBe 1
    processTypes.keys.take(1) shouldBe Set("streaming")
  }

  test("should optionally load scenarioTypes configuration") {
    val unresolvedConfig = ConfigFactory.parseMap(Map[String, Any](
      "scenarioTypes.newStreamingScenario" -> ConfigValueFactory.fromAnyRef(oldConfiguration.getConfig("processTypes.streaming").root())
    ).asJava)

    val processTypes = ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(LoadedConfig.load(oldConfiguration.withFallback(unresolvedConfig)))

    processTypes.size shouldBe 1
    processTypes.keys.take(1) shouldBe Set("newStreamingScenario")
  }

  test("should throw when required configuration is missing") {
    val unresolvedConfig = ConfigFactory.parseString(
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
        |  }
        |}
        |""".stripMargin
    )

    intercept[typesafe.config.ConfigException] {
      ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(LoadedConfig.load(unresolvedConfig))
    }.getMessage should include("No configuration setting found for key 'deploymentConfig.type'")
  }

  test("should throw when no configuration is provided") {
    val unresolvedConfig = ConfigFactory.parseString(
      """
        |test {}
        |""".stripMargin
    )

    intercept[RuntimeException] {
      ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(LoadedConfig.load(unresolvedConfig))
    }.getMessage should include("No scenario types configuration provided")
  }

}

package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.FunSuite
import org.scalatest.Matchers.{convertToAnyShouldWrapper, include}

class ProcessingTypeDataConfigurationReaderSpec extends FunSuite {

  private val oldConfiguration: Config = ConfigFactory.parseString(
    """
      |processTypes {
      |  "streaming" {
      |    engineConfig {
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
    val processTypes = ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(oldConfiguration)

    processTypes.size shouldBe 1
    processTypes.keys.take(1) shouldBe Set("streaming")
  }

  test("should optionally load scenarioTypes configuration") {
    val configuration = ConfigFactory.parseMap(Map[String, Any](
      "scenarioTypes.newStreamingScenario" -> ConfigValueFactory.fromAnyRef(oldConfiguration.getConfig("processTypes.streaming").root())
    ).asJava)

    val config = oldConfiguration.withFallback(configuration).resolve()

    val processTypes = ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(config)

    processTypes.size shouldBe 1
    processTypes.keys.take(1) shouldBe Set("newStreamingScenario")
  }

  test("should throw when required configuration is missing") {
    val configuration = ConfigFactory.parseString(
      """
        |scenarioTypes {
        |  "streaming" {
        |    engineConfig {
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

    val config = configuration.resolve()

    intercept[typesafe.config.ConfigException] {
      ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(config)
    }.getMessage should include("No configuration setting found for key 'engineConfig.type'")
  }

  test("should throw when no configuration is provided") {
    val configuration = ConfigFactory.parseString(
      """
        |test {}
        |""".stripMargin
    )

    val config = configuration.resolve()

    intercept[RuntimeException] {
      ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(config)
    }.getMessage should include("No scenario types configuration provided")
  }

}

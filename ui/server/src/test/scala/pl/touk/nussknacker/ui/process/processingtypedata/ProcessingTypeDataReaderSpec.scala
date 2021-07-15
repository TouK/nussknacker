package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.FunSuite
import org.scalatest.Matchers.{convertToAnyShouldWrapper, include}

class ProcessingTypeDataReaderSpec extends FunSuite {

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
      |      classPath: ["engine/flink/management/sample/target/scala-2.12/managementSample.jar"]
      |    }
      |  }
      |}
      |""".stripMargin
  )

  import scala.collection.JavaConverters._

  test("should load old processTypes configuration") {
    val processTypes = ProcessingTypeDataReader.loadProcessingTypeData(oldConfiguration)

    processTypes.all.size shouldBe 1
    processTypes.all.keys.take(1) shouldBe Set("streaming")
  }

  test("should optionally load scenarioTypes configuration") {
    val configuration = ConfigFactory.parseMap(Map[String, Any](
      "scenarioTypes.newStreamingScenario" -> ConfigValueFactory.fromAnyRef(oldConfiguration.getConfig("processTypes.streaming").root())
    ).asJava)

    val config = oldConfiguration.withFallback(configuration).resolve()

    val processTypes = ProcessingTypeDataReader.loadProcessingTypeData(config)

    processTypes.all.size shouldBe 1
    processTypes.all.keys.take(1) shouldBe Set("newStreamingScenario")
  }

  test("should throw when required configuration is missing") {
    val configuration = ConfigFactory.parseString(
      """
        |scenarioTypes {
        |  "streaming" {
        |    engineConfig {
        |      restUrlMissing: "http://localhost:8081"
        |      type: "flinkStreaming"
        |    }
        |
        |    modelConfig {
        |      classPath: ["engine/flink/management/sample/target/scala-2.12/managementSample.jar"]
        |    }
        |  }
        |}
        |""".stripMargin
    )

    val config = configuration.resolve()

    intercept[typesafe.config.ConfigException] {
      ProcessingTypeDataReader.loadProcessingTypeData(config)
    }.getMessage should include("No configuration setting found for key 'root.restUrl'")
  }

  test("should throw when no configuration is provided") {
    val configuration = ConfigFactory.parseString(
      """
        |test {}
        |""".stripMargin
    )

    val config = configuration.resolve()

    intercept[RuntimeException] {
      ProcessingTypeDataReader.loadProcessingTypeData(config)
    }.getMessage should include("No scenario types configuration provided")
  }

}

package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.FunSuite
import org.scalatest.Matchers.{convertToAnyShouldWrapper, include}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

class ProcessingTypeDataReaderSpec extends FunSuite {

  private val baseConfiguration: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(ConfigFactory.parseResources("ui.conf"))

  import scala.collection.JavaConverters._

  test("should load old processTypes configuration") {
    val processTypes = ProcessingTypeDataReader.loadProcessingTypeData(baseConfiguration)

    processTypes.all.size shouldBe 1
    processTypes.all.keys.take(1) shouldBe Set("streaming")
  }

  test("should optionally load scenarioTypes configuration") {
    val configuration = ConfigFactory.parseMap(Map[String, Any](
      "scenarioTypes.newStreamingScenario" -> ConfigValueFactory.fromAnyRef(baseConfiguration.getConfig("processTypes.streaming").root())
    ).asJava)

    val config = baseConfiguration.withFallback(configuration).resolve()

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

    val config = configuration.resolve()

    intercept[typesafe.config.ConfigException] {
      ProcessingTypeDataReader.loadProcessingTypeData(config)
    }.getMessage should include("No configuration setting found for key 'root.jobManagerTimeout'")
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

package pl.touk.nussknacker.engine

import java.util.Collections
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.config.LoadedConfig
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class DefaultModelConfigLoaderTest extends FunSuite with Matchers {

  private val inputConfig = ConfigFactory.parseMap(Collections.singletonMap("property1", "value1"))

  test("should handle absence of model.conf") {
    val loader = new DefaultModelConfigLoader {
      override def modelConfigResource: String = "notExist.conf"
    }

    val config = loader.resolveConfig(InputConfigDuringExecution(inputConfig), getClass.getClassLoader)

    config.getString("property1") shouldBe "value1"
    config.getString("property2") shouldBe "shouldBeOverriden"
    config.getString("testProperty") shouldBe "testValue"
    config.hasPath("otherProperty") shouldBe false
  }

  test("should load model.conf and override with given") {
    val config = LocalModelData(inputConfig, new EmptyProcessConfigCreator).processConfig

    config.getString("property1") shouldBe "value1"
    config.getString("property2") shouldBe "value1Suffix"
    config.getString("testProperty") shouldBe "testValue"
    config.getBoolean("otherProperty") shouldBe true
  }

  test("should load only input config during execution") {
    val config = LocalModelData(inputConfig, new EmptyProcessConfigCreator).inputConfigDuringExecution.config

    config.getString("property1") shouldBe "value1"
    config.hasPath("property2") shouldBe false
    config.hasPath("testProperty") shouldBe false
    config.hasPath("otherProperty") shouldBe false
  }

  test("should not load application.conf") {
    val config = LocalModelData(inputConfig, new EmptyProcessConfigCreator).processConfig

    config.hasPath("shouldNotLoad") shouldBe false

  }

  test("should not load environment variables before execution") {
    val config = ConfigFactory.parseString(
    """deploymentConfig {
      |  type: fooType
      |}
      |modelConfig {
      |  classPath: []
      |  systemEnv: ${LANG}
      |}
      |""".stripMargin)
    val processingTypeConfig = ProcessingTypeConfig.read(LoadedConfig.load(config))

    val loader = new DefaultModelConfigLoader
    val configUsedDuringExecution = loader.resolveInputConfigDuringExecution(processingTypeConfig.unresolvedModelConfig, getClass.getClassLoader)

    a[ConfigException.NotResolved] should be thrownBy {
      configUsedDuringExecution.config.getString("systemEnv")
    }
  }

  test("should be able to use variables defined on root level inside model config") {
    val config = ConfigFactory.parseString(
      """rootLevelVariable: foo
        |
        |scenarioTypes {
        |  streaming {
        |    deploymentConfig {
        |      type: fooType
        |    }
        |    modelConfig {
        |      classPath: []
        |      someVariable: ${rootLevelVariable}
        |    }
        |  }
        |}
        |""".stripMargin)
    val processingTypeConfig = ProcessingTypeConfig.read(LoadedConfig.load(config).get("scenarioTypes.streaming"))

    val loader = new DefaultModelConfigLoader
    val configUsedDuringExecution = loader.resolveInputConfigDuringExecution(processingTypeConfig.unresolvedModelConfig, getClass.getClassLoader)

    configUsedDuringExecution.config.getString("someVariable") shouldEqual "foo"
  }

}

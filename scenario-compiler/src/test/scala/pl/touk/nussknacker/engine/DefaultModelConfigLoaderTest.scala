package pl.touk.nussknacker.engine

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution}
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.util.Collections

class DefaultModelConfigLoaderTest extends AnyFunSuite with Matchers {

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
    val config = LocalModelData(inputConfig, List.empty).modelConfig

    config.getString("property1") shouldBe "value1"
    config.getString("property2") shouldBe "value1Suffix"
    config.getString("testProperty") shouldBe "testValue"
    config.getBoolean("otherProperty") shouldBe true
  }

  test("should load only input config during execution") {
    val config =
      LocalModelData(inputConfig, List.empty).inputConfigDuringExecution.config

    config.getString("property1") shouldBe "value1"
    config.hasPath("property2") shouldBe false
    config.hasPath("testProperty") shouldBe false
    config.hasPath("otherProperty") shouldBe false
  }

  test("should resolve environment variables") {
    val config  = LocalModelData(ConfigFactory.empty(), List.empty).modelConfig
    val envPath = System.getenv("PATH")

    envPath shouldNot be(null)
    config.getString("envPathProperty") shouldBe envPath
  }

  test("should not load application.conf") {
    val config = LocalModelData(inputConfig, List.empty).modelConfig

    config.hasPath("shouldNotLoad") shouldBe false
  }

  test("should not contain java.class.path") {
    val config = LocalModelData(inputConfig, List.empty).modelConfig

    // classpath can grow very long and there's a 65 KB limit on a single String value in Configuration
    // that we already hit in CI, see: https://github.com/lightbend/config/issues/627
    config.hasPath("java.class.path") shouldBe false
  }

}

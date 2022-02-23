package pl.touk.nussknacker.engine

import java.util.Collections
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution}
import pl.touk.nussknacker.engine.testing.LocalModelData

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
}

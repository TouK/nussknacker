package pl.touk.nussknacker.engine

import java.util.Collections

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class DefaultModelConfigLoaderTest extends FunSuite with Matchers {

  private val inputConfig = ConfigFactory.parseMap(Collections.singletonMap("property1", "value1"))

  test("should handle absence of model.conf") {
    val loader = new DefaultModelConfigLoader {
      override def modelConfigResource: String = "notExist.conf"
    }

    val config = loader.resolveFullConfig(inputConfig, getClass.getClassLoader)

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

  test("should load only config passed in execution") {
    val config = ConfigFactory.parseString(LocalModelData(inputConfig, new EmptyProcessConfigCreator).serializedConfigPassedInExecution)

    config.getString("property1") shouldBe "value1"
    config.hasPath("property2") shouldBe false
    config.hasPath("testProperty") shouldBe false
    config.hasPath("otherProperty") shouldBe false
  }
}

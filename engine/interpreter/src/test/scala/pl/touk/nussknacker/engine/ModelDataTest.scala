package pl.touk.nussknacker.engine

import java.util.Collections

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}

class ModelDataTest extends FunSuite with Matchers {

  test("should handle absence of model.conf") {

    val config = new ModelConfigToLoad(ConfigFactory.parseMap(Collections.singletonMap("property1", "value1"))) {
      override def modelConfigResource: String = "notExist.conf"
    }.loadConfig(getClass.getClassLoader)

    config.getString("property1") shouldBe "value1"
    config.getString("property2") shouldBe "shouldBeOverriden"
    config.getString("testProperty") shouldBe "testValue"
    config.hasPath("otherProperty") shouldBe false

  }

  test("should load model.conf and override with given") {

    val config = LocalModelData(ConfigFactory.parseMap(Collections.singletonMap("property1", "value1")), new EmptyProcessConfigCreator).processConfig

    config.getString("property1") shouldBe "value1"
    config.getString("property2") shouldBe "value1Suffix"
    config.getString("testProperty") shouldBe "testValue"
    config.getBoolean("otherProperty") shouldBe true

  }

}

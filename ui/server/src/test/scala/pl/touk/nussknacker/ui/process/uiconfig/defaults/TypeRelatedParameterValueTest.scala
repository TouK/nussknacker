package pl.touk.nussknacker.ui.process.uiconfig.defaults

import org.scalatest.{FlatSpec, Matchers}

class TypeRelatedParameterValueTest extends FlatSpec with Matchers {
  behavior of "TypeRelatedParameterValueTest"

  private def testTypeRelatedDefaultValue(classRef: String, value: Any, paramName: String = "") = {
    it should s"give value $value for type $classRef" in {
      TypeRelatedParameterValueExtractor.evaluateTypeRelatedParamValue(paramName, classRef) shouldBe value.toString
    }
  }

  testTypeRelatedDefaultValue("int", 0)
  testTypeRelatedDefaultValue("short", 0)
  testTypeRelatedDefaultValue("long", 0)

  testTypeRelatedDefaultValue("float", 0f)
  testTypeRelatedDefaultValue("double", 0f)
  testTypeRelatedDefaultValue("java.math.BigDecimal", 0f)

  testTypeRelatedDefaultValue("java.lang.String", "''")
  testTypeRelatedDefaultValue("boolean", true)
  testTypeRelatedDefaultValue("someCrazyValue", "", "someCrazyVal")
  testTypeRelatedDefaultValue("java.util.List", "{}")
  testTypeRelatedDefaultValue("java.util.Map", "{:}")
}

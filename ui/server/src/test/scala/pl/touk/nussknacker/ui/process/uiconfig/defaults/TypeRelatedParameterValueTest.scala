package pl.touk.nussknacker.ui.process.uiconfig.defaults

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, LocalTime}

import org.scalatest.{FlatSpec, Matchers}

class TypeRelatedParameterValueTest extends FlatSpec with Matchers {
  behavior of "TypeRelatedParameterValueTest"

  private val now: LocalDateTime = LocalDateTime.of(LocalDate.now, LocalTime.MIN)

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
  testTypeRelatedDefaultValue("someCrazyValue", "#someCrazyVal", "someCrazyVal")
  testTypeRelatedDefaultValue("java.util.List", "{}")
  testTypeRelatedDefaultValue("java.util.Map", "{:}")

  testTypeRelatedDefaultValue("java.time.LocalDateTime",  s"T(java.time.LocalDateTime).parse('${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}')")
  testTypeRelatedDefaultValue("java.time.LocalDate",  s"T(java.time.LocalDate).parse('${now.format(DateTimeFormatter.ISO_LOCAL_DATE)}')")
  testTypeRelatedDefaultValue("java.time.LocalTime",  "T(java.time.LocalTime).parse('00:00')")
}

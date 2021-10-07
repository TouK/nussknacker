package pl.touk.nussknacker.engine.definition.parameter.defaults

import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.definition.parameter.defaults.TypeRelatedParameterValueDeterminer._

class TypeRelatedParameterValueDeterminerTest extends FunSuite with Matchers with OptionValues {

  test("defaults for common types") {
    determineTypeRelatedDefaultParamValue(classOf[Int]).value shouldBe "0"
    determineTypeRelatedDefaultParamValue(classOf[Short]).value shouldBe "0"
    determineTypeRelatedDefaultParamValue(classOf[Long]).value shouldBe "0"
    determineTypeRelatedDefaultParamValue(classOf[java.lang.Long]).value shouldBe "0"
    determineTypeRelatedDefaultParamValue(classOf[java.lang.Short]).value shouldBe "0"
    determineTypeRelatedDefaultParamValue(classOf[java.lang.Integer]).value shouldBe "0"
    determineTypeRelatedDefaultParamValue(classOf[java.math.BigInteger]).value shouldBe "0"

    determineTypeRelatedDefaultParamValue(classOf[Float]).value shouldBe "0.0"
    determineTypeRelatedDefaultParamValue(classOf[Double]).value shouldBe "0.0"
    determineTypeRelatedDefaultParamValue(classOf[java.lang.Float]).value shouldBe "0.0"
    determineTypeRelatedDefaultParamValue(classOf[java.lang.Double]).value shouldBe "0.0"
    determineTypeRelatedDefaultParamValue(classOf[java.math.BigDecimal]).value shouldBe "0.0"

    determineTypeRelatedDefaultParamValue(classOf[String]).value shouldBe "''"
    determineTypeRelatedDefaultParamValue(classOf[Boolean]).value shouldBe "true"
    determineTypeRelatedDefaultParamValue(classOf[java.util.List[_]]).value shouldBe "{}"
    determineTypeRelatedDefaultParamValue(classOf[java.util.Map[_, _]]).value shouldBe "{:}"

    determineTypeRelatedDefaultParamValue(classOf[SomeClass]) shouldBe empty
  }

  trait SomeClass
}

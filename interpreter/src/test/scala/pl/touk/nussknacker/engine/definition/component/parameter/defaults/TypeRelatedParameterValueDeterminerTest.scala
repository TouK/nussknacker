package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.TypeRelatedParameterValueDeterminer._

class TypeRelatedParameterValueDeterminerTest extends AnyFunSuite with Matchers with OptionValues {

  test("defaults for common types") {
    determineTypeRelatedDefaultParamValue(None, classOf[Int]).value.expression shouldBe "0"
    determineTypeRelatedDefaultParamValue(None, classOf[Short]).value.expression shouldBe "0"
    determineTypeRelatedDefaultParamValue(None, classOf[Long]).value.expression shouldBe "0"
    determineTypeRelatedDefaultParamValue(None, classOf[java.lang.Long]).value.expression shouldBe "0"
    determineTypeRelatedDefaultParamValue(None, classOf[java.lang.Short]).value.expression shouldBe "0"
    determineTypeRelatedDefaultParamValue(None, classOf[java.lang.Integer]).value.expression shouldBe "0"
    determineTypeRelatedDefaultParamValue(None, classOf[java.math.BigInteger]).value.expression shouldBe "0"

    determineTypeRelatedDefaultParamValue(None, classOf[Float]).value.expression shouldBe "0.0"
    determineTypeRelatedDefaultParamValue(None, classOf[Double]).value.expression shouldBe "0.0"
    determineTypeRelatedDefaultParamValue(None, classOf[java.lang.Float]).value.expression shouldBe "0.0"
    determineTypeRelatedDefaultParamValue(None, classOf[java.lang.Double]).value.expression shouldBe "0.0"
    determineTypeRelatedDefaultParamValue(None, classOf[java.math.BigDecimal]).value.expression shouldBe "0.0"

    determineTypeRelatedDefaultParamValue(None, classOf[String]).value.expression shouldBe "''"
    determineTypeRelatedDefaultParamValue(None, classOf[Boolean]).value.expression shouldBe "true"
    determineTypeRelatedDefaultParamValue(None, classOf[java.util.List[_]]).value.expression shouldBe "{}"
    determineTypeRelatedDefaultParamValue(None, classOf[java.util.Map[_, _]]).value.expression shouldBe "{:}"

    determineTypeRelatedDefaultParamValue(None, classOf[SomeClass]) shouldBe empty
  }

  trait SomeClass
}

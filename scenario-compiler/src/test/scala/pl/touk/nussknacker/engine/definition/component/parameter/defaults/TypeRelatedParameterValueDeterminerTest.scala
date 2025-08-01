package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.SpelParameterEditor
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.TypeRelatedParameterValueDeterminer._
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

class TypeRelatedParameterValueDeterminerTest extends AnyFunSuite with Matchers with OptionValues {

  test("defaults for common types") {
    determineTypeRelatedDefaultParamValue(SpelParameterEditor, Typed[Long]).value shouldBe "0".spel
    determineTypeRelatedDefaultParamValue(SpelParameterEditor, Typed[Short]).value shouldBe "0".spel
    determineTypeRelatedDefaultParamValue(SpelParameterEditor, Typed[Int]).value shouldBe "0".spel
    determineTypeRelatedDefaultParamValue(
      SpelParameterEditor,
      Typed[java.math.BigInteger]
    ).value shouldBe "0".spel

    determineTypeRelatedDefaultParamValue(SpelParameterEditor, Typed[Float]).value shouldBe "0.0".spel
    determineTypeRelatedDefaultParamValue(SpelParameterEditor, Typed[Double]).value shouldBe "0.0".spel
    determineTypeRelatedDefaultParamValue(
      SpelParameterEditor,
      Typed[java.math.BigDecimal]
    ).value shouldBe "0.0".spel

    determineTypeRelatedDefaultParamValue(SpelParameterEditor, Typed[String]).value shouldBe "''".spel
    determineTypeRelatedDefaultParamValue(SpelParameterEditor, Typed[Boolean]).value shouldBe "true".spel
    determineTypeRelatedDefaultParamValue(SpelParameterEditor, Typed[java.util.List[_]]).value shouldBe "{}".spel
    determineTypeRelatedDefaultParamValue(
      SpelParameterEditor,
      Typed[java.util.Map[_, _]]
    ).value shouldBe "{:}".spel

    determineTypeRelatedDefaultParamValue(SpelParameterEditor, Typed[SomeClass]).value shouldBe "".spel
  }

  trait SomeClass
}

package pl.touk.nussknacker.ui.process.uiconfig.defaults

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedExpressionValues, Parameter, ParameterRestriction}
import pl.touk.nussknacker.engine.definition.defaults.NodeDefinition

class RestrictionBasedDefaultValueExtractorTest extends FunSuite with Matchers {

  private val definition = NodeDefinition("id", List())

  private def evaluate(restriction: Option[ParameterRestriction]) = {
    RestrictionBasedDefaultValueExtractor.evaluateParameterDefaultValue(definition,
          Parameter("id", ClazzRef[String], ClazzRef[String], restriction))
  }

  test("extracts first value for restriction") {
    val constraint = Some(FixedExpressionValues(List(FixedExpressionValue("expr1", "label1"),
      FixedExpressionValue("expr2", "label2"))))
    evaluate(constraint) shouldBe Some("expr1")
  }

  test("skip parameter without restriction") {
    evaluate(None) shouldBe None
  }

}

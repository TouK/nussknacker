package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

class EditorPossibleValuesBasedDefaultValueDeterminerTest extends AnyFunSuite with Matchers {

  test("determine default param value from first value from fixed values editor possible values") {
    val fixedValuesEditor = List(
      FixedValuesParameterEditor(
        List(
          FixedExpressionValue("expr1", "label1"),
          FixedExpressionValue("expr2", "label2")
        )
      )
    )

    determine(fixedValuesEditor) shouldBe Some(Expression.spel("expr1"))
  }

  test("determine default param value from first value from fixed values editor possible values in dual mode") {
    val fixedValuesEditor = List(
      FixedValuesParameterEditor(
        List(
          FixedExpressionValue("expr1", "label1"),
          FixedExpressionValue("expr2", "label2")
        )
      ),
      SpelParameterEditor,
    )

    determine(fixedValuesEditor) shouldBe Some(Expression.spel("expr1"))
  }

  test("determine default param value for dictionary parameter editor") {
    val dictParam = List(DictParameterEditor("someDictId"))

    determine(dictParam) shouldBe Some(Expression(Language.DictKeyWithLabel, ""))
  }

  test("not determine default param value from editors without possible values") {
    val booleanParam        = List(BoolParameterEditor)
    val spelParameterEditor = List(SpelParameterEditor)

    determine(booleanParam) shouldBe None

    determine(spelParameterEditor) shouldBe None
  }

  private def determine(editors: List[ParameterEditor]): Option[Expression] = {
    EditorPossibleValuesBasedDefaultValueDeterminer.determineParameterDefaultValue(
      DefaultValueDeterminerParameters(
        ParameterData(Unknown, annotations = List.empty, isLazyParameter = false),
        isOptional = false,
        ParameterConfig.empty,
        editors
      )
    )
  }

}

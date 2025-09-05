package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.NonEmptyList
import cats.data.Validated.{Valid, invalidNel}
import org.scalatest.Inspectors.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{UnsupportedDictParameterEditorType, UnsupportedFixedValuesType}
import pl.touk.nussknacker.engine.api.definition.{DictParameterEditor, FixedExpressionValue, FixedValuesParameterEditor}
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ValueInputWithDictEditor, ValueInputWithFixedValuesProvided}
import pl.touk.nussknacker.engine.compile.nodecompilation.FragmentParameterValidator.permittedTypesForEditors
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentClazzRef
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class FragmentParameterValidatorTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  test("should be valid when validating with permitted types for value input with dict editor") {
    forAll(permittedTypesForEditors) { fragmentParameterType: FragmentClazzRef =>
      {
        val dictId = "someDictId"
        val result = FragmentParameterValidator(emptyClassDefinitionSet).validateAgainstClazzRefAndGetEditor(
          valueEditor = ValueInputWithDictEditor(dictId, allowOtherValue = false),
          initialValue = None,
          refClazz = fragmentParameterType,
          paramName = ParameterName("someParamName"),
          nodeIds = Set(NodeId("someNodeId"))
        )
        result.validValue shouldBe NonEmptyList.one(DictParameterEditor(dictId))
      }
    }
  }

  test("should be valid when validating with permitted types for value input with fixed values editor") {
    forAll(permittedTypesForEditors) { fragmentParameterType: FragmentClazzRef =>
      {
        val fixedValuesList = List(FixedExpressionValue("someExpression", "someLabel"))
        val result = FragmentParameterValidator(emptyClassDefinitionSet).validateAgainstClazzRefAndGetEditor(
          valueEditor = ValueInputWithFixedValuesProvided(fixedValuesList, allowOtherValue = false),
          initialValue = None,
          refClazz = fragmentParameterType,
          paramName = ParameterName("someParamName"),
          nodeIds = Set(NodeId("someNodeId"))
        )
        result.validValue shouldBe NonEmptyList.one(
          FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: fixedValuesList)
        )
      }
    }
  }

  test("should not be valid when validating with not permitted type for value input with dict editor") {
    val paramName            = ParameterName("someParamName")
    val invalidParameterType = FragmentClazzRef[java.lang.Double]
    val nodeIds              = Set(NodeId("someNodeId"))
    val result = FragmentParameterValidator(emptyClassDefinitionSet).validateAgainstClazzRefAndGetEditor(
      valueEditor = ValueInputWithDictEditor("someDictId", allowOtherValue = false),
      initialValue = None,
      refClazz = invalidParameterType,
      paramName = paramName,
      nodeIds = nodeIds
    )
    result shouldBe invalidNel(
      UnsupportedDictParameterEditorType(paramName, invalidParameterType.refClazzName, nodeIds)
    )
  }

  test("should not be valid when validating with not permitted type for value input with fixed values editor") {
    val paramName            = ParameterName("someParamName")
    val invalidParameterType = FragmentClazzRef[java.lang.Double]
    val nodeIds              = Set(NodeId("someNodeId"))
    val result = FragmentParameterValidator(emptyClassDefinitionSet).validateAgainstClazzRefAndGetEditor(
      valueEditor = ValueInputWithFixedValuesProvided(
        List(FixedExpressionValue("someExpression", "someLabel")),
        allowOtherValue = false
      ),
      initialValue = None,
      refClazz = invalidParameterType,
      paramName = paramName,
      nodeIds = nodeIds
    )
    result shouldBe invalidNel(UnsupportedFixedValuesType(paramName, invalidParameterType.refClazzName, nodeIds))
  }

  private def emptyClassDefinitionSet = ClassDefinitionSet(Set.empty[ClassDefinition])

}

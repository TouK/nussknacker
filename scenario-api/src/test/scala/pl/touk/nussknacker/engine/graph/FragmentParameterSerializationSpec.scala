package pl.touk.nussknacker.engine.graph

import io.circe.jawn.decode
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterName,
  ParameterValueCompileTimeValidation,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}

class FragmentParameterSerializationSpec extends AnyFunSuite with Matchers {

  test(
    "should deserialize FragmentParameter without required, initialValue, hintText, valueEditor, valueCompileTimeValidation [backwards compatibility test]"
  ) {
    val referenceFragmentParameter = FragmentParameter(
      ParameterName("paramString"),
      FragmentClazzRef("java.lang.String"),
      required = false,
      initialValue = None,
      hintText = None,
      valueEditor = None,
      valueCompileTimeValidation = None
    )

    decode[FragmentParameter]("""{
      |  "name" : "paramString",
      |  "typ" : {
      |    "refClazzName" : "java.lang.String"
      |  }
      |}""".stripMargin) shouldBe Right(referenceFragmentParameter)

    decode[FragmentParameter]("""{
        |  "name" : "paramString",
        |  "typ" : {
        |    "refClazzName" : "java.lang.String"
        |  },
        |  "required" : false,
        |  "initialValue" : null,
        |  "hintText" : null,
        |  "valueEditor" : null,
        |  "valueCompileTimeValidation" : null
        |}""".stripMargin) shouldBe Right(referenceFragmentParameter)
  }

  test("should deserialize FragmentParameter") {
    decode[FragmentParameter]("""{
      "name" : "paramString",
      "typ" : {
        "refClazzName" : "java.lang.String"
      },
      "required" : true,
      "initialValue" : {
        "expression" : "'someValue'",
        "label" : "someValue"
      },
      "hintText" : "some hint text",
      "valueEditor" : {
        "type": "ValueInputWithFixedValuesProvided",
        "allowOtherValue" : true,
        "fixedValuesList" : [
          {
            "expression" : "'someValue'",
            "label" : "someValue"
          },
          {
            "expression" : "'someOtherValue'",
            "label" : "someOtherValue"
          }
        ]
      },
      "valueCompileTimeValidation" : {
        "validationExpression" : {
          "expression" : "#value.length() < 7",
          "language" : "spel"
        },
        "validationFailedMessage" : "some failed message"
      }
    }""") shouldBe Right(
      FragmentParameter(
        ParameterName("paramString"),
        FragmentClazzRef[String],
        required = true,
        initialValue = Some(FixedExpressionValue("'someValue'", "someValue")),
        hintText = Some("some hint text"),
        valueEditor = Some(
          ValueInputWithFixedValuesProvided(
            fixedValuesList = List(
              FixedExpressionValue("'someValue'", "someValue"),
              FixedExpressionValue("'someOtherValue'", "someOtherValue")
            ),
            allowOtherValue = true
          )
        ),
        valueCompileTimeValidation = Some(
          ParameterValueCompileTimeValidation(Expression.spel("#value.length() < 7"), Some("some failed message"))
        ),
      )
    )
  }

}

package pl.touk.nussknacker.engine.graph

import io.circe.jawn.decode
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue,
  FragmentClazzRef,
  FragmentParameter,
  ValidationExpression,
  ValueInputWithFixedValuesProvided
}

class FragmentParameterSerializationSpec extends AnyFunSuite with Matchers {

  // todo add validationExpression
  test(
    "should deserialize FragmentParameter without required, initialValue, hintText, valueEditor, validationExpression [backwards compatibility test]"
  ) {
    val referenceFragmentParameter = FragmentParameter(
      "paramString",
      FragmentClazzRef("java.lang.String"),
      required = false,
      initialValue = None,
      hintText = None,
      valueEditor = None,
      validationExpression = None
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
        |  "validationExpression" : null
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
      "validationExpression" : {
        "expression" : {
          "expression" : "#value.length() < 7",
          "language" : "spel"
        },
        "failedMessage" : "some failed message"
      }
    }""") shouldBe Right(
      FragmentParameter(
        "paramString",
        FragmentClazzRef[String],
        required = true,
        initialValue = Some(FixedExpressionValue("'someValue'", "someValue")),
        hintText = Some("some hint text"),
        valueEditor = Some(
          ValueInputWithFixedValuesProvided(
            fixedValuesList = List(
              FragmentInputDefinition.FixedExpressionValue("'someValue'", "someValue"),
              FragmentInputDefinition.FixedExpressionValue("'someOtherValue'", "someOtherValue")
            ),
            allowOtherValue = true
          )
        ),
        validationExpression =
          Some(ValidationExpression(Expression.spel("#value.length() < 7"), Some("some failed message"))),
      )
    )
  }

}

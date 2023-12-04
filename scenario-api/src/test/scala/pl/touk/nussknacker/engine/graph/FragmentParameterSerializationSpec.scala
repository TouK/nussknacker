package pl.touk.nussknacker.engine.graph

import io.circe.jawn.decode
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue,
  FragmentClazzRef,
  FragmentParameter,
  ValueInputWithFixedValuesPreset,
  ValueInputWithFixedValuesProvided
}

class FragmentParameterSerializationSpec extends AnyFunSuite with Matchers {

  test(
    "should deserialize FragmentParameter without required, initialValue, hintText, valueEditor [backwards compatibility test]"
  ) {
    val referenceFragmentParameter = FragmentParameter(
      "paramString",
      FragmentClazzRef("java.lang.String"),
      required = false,
      initialValue = None,
      hintText = None,
      valueEditor = None
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
        |  "valueEditor" : null
        |}""".stripMargin) shouldBe Right(referenceFragmentParameter)
  }

  test("should deserialize FragmentParameter - ValueInputWithFixedValuesProvided") {
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
        )
      )
    )
  }

  test("should deserialize FragmentParameter - ValueInputWithFixedValuesPreset") {
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
        "type": "ValueInputWithFixedValuesPreset",
        "allowOtherValue" : false,
        "fixedValuesListPresetId" : "presetId"
      }
    }""") shouldBe Right(
      FragmentParameter(
        "paramString",
        FragmentClazzRef[String],
        required = true,
        initialValue = Some(FixedExpressionValue("'someValue'", "someValue")),
        hintText = Some("some hint text"),
        valueEditor = Some(
          ValueInputWithFixedValuesPreset(
            fixedValuesListPresetId = "presetId",
            allowOtherValue = false
          )
        )
      )
    )
  }

}

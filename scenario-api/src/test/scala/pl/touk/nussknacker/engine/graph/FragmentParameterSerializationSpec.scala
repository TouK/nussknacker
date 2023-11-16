package pl.touk.nussknacker.engine.graph

import io.circe.jawn.decode
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.ParameterInputMode.{
  InputModeAny,
  InputModeFixedList
}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue,
  FragmentClazzRef,
  FragmentParameter,
  ParameterInputConfig
}

class FragmentParameterSerializationSpec extends AnyFunSuite with Matchers {

  test(
    "should deserialize FragmentParameter without required, initialValue, hintText, inputConfig [backwards compatibility test]"
  ) {
    val referenceFragmentParameter = FragmentParameter(
      "paramString",
      FragmentClazzRef("java.lang.String"),
      required = false,
      initialValue = None,
      hintText = None,
      inputConfig = ParameterInputConfig(InputModeAny, None)
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
        |  "inputConfig" : {
        |    "inputMode" : "InputModeAny",
        |    "fixedValuesList" : null
        |  }
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
      "inputConfig" : {
        "inputMode" : "InputModeFixedList",
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
        inputConfig = ParameterInputConfig(
          inputMode = InputModeFixedList,
          fixedValuesList = Some(
            List(
              FragmentInputDefinition.FixedExpressionValue("'someValue'", "someValue"),
              FragmentInputDefinition.FixedExpressionValue("'someOtherValue'", "someOtherValue")
            )
          )
        )
      )
    )
  }

}

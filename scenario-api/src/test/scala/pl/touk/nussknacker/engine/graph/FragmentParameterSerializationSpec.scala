package pl.touk.nussknacker.engine.graph

import io.circe.syntax.EncoderOps
import io.circe.{Json, parser}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameterInputMode.{
  InputModeAny,
  InputModeFixedList
}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue => FragmentFixedExpressionValue,
  FragmentClazzRef,
  FragmentParameter,
  FragmentParameterFixedValuesUserDefinedList,
  FragmentParameterNoFixedValues
}

class FragmentParameterSerializationSpec extends AnyFunSuite with Matchers {

  test("properly serialize and deserialize FragmentParameterNoFixedValues") {
    val referenceParameter = FragmentParameterNoFixedValues(
      "name",
      FragmentClazzRef[String],
      required = true,
      initialValue = Some(FragmentFixedExpressionValue("'Tomasz'", "Tomasz")),
      hintText = Some("some hint text"),
      inputMode = InputModeAny
    )

    val json =
      """{
        |  "name" : "name",
        |  "typ" : {
        |    "refClazzName" : "java.lang.String"
        |  },
        |  "required" : true,
        |  "initialValue" : {
        |    "expression" : "'Tomasz'",
        |    "label" : "Tomasz"
        |  },
        |  "hintText" : "some hint text",
        |  "inputMode" : "InputModeAny"
        |}""".stripMargin

    val parsedJson            = parser.parse(json).toOption.get
    val deserializedParameter = parsedJson.as[FragmentParameter].toOption.get

    referenceParameter.asJson shouldBe parsedJson
    deserializedParameter shouldBe referenceParameter
  }

  test("properly serialize and deserialize FragmentParameterFixedValuesUserDefinedList") {
    val fixedValuesList =
      List(FragmentFixedExpressionValue("'aaa'", "aaa"), FragmentFixedExpressionValue("'bbb'", "bbb"))

    val referenceParameter = FragmentParameterFixedValuesUserDefinedList(
      "name",
      FragmentClazzRef[String],
      required = false,
      fixedValuesList = fixedValuesList,
      initialValue = None,
      hintText = None,
      inputMode = InputModeFixedList
    )

    val json =
      """{
        |  "name" : "name",
        |  "typ" : {
        |    "refClazzName" : "java.lang.String"
        |  },
        |  "required" : false,
        |  "initialValue" : null,
        |  "hintText" : null,
        |  "inputMode" : "InputModeFixedList",
        |  "fixedValuesList" : [
        |    {
        |      "expression" : "'aaa'",
        |      "label" : "aaa"
        |    },
        |    {
        |      "expression" : "'bbb'",
        |      "label" : "bbb"
        |    }
        |  ]
        |}""".stripMargin

    val parsedJson            = parser.parse(json).toOption.get
    val deserializedParameter = parsedJson.as[FragmentParameter].toOption.get

    referenceParameter.asJson shouldBe parsedJson
    deserializedParameter shouldBe referenceParameter
  }

}

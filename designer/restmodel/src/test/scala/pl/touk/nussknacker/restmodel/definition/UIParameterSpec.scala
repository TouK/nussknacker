package pl.touk.nussknacker.restmodel.definition

import io.circe.Decoder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.definition.StringParameterEditor
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/**
 * It's a temporary test which verifies further compatibility decoding json
 */
class UIParameterSpec extends AnyFunSuite with Matchers {

  import UIParameter.decoder
  import io.circe.syntax._

  private implicit val typingResultDecoder: Decoder[TypingResult] = Decoder.decodeJson.map(_ => typing.Unknown)

  private val expected = UIParameter("Topic", typing.Unknown, StringParameterEditor, Nil, "", Map.empty, Set.empty, false)

  test("deserialize json with string expression (without language) in default value") {
    verify(expected.asJson.spaces2)
  }

  test("deserialize json with expression in default value") {
    val json =
      """
        |{
        |    "name": "Topic",
        |    "typ" : {},
        |    "editor" : {
        |      "type" : "StringParameterEditor"
        |    },
        |    "validators": [],
        |    "defaultValue": {
        |     "language": "spel",
        |     "expression": ""
        |    },
        |    "additionalVariables": {},
        |    "variablesToHide": [],
        |    "branchParam": false
        |  }
        |""".stripMargin
    verify(json)
  }

  private def verify(json: String) = {
    val result = CirceUtil.decodeJsonUnsafe[UIParameter](json)
    result shouldBe expected
  }

}

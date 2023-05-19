package pl.touk.nussknacker.restmodel.definition

import io.circe.Decoder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.definition.StringParameterEditor
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression

/**
 * It's a temporary test which verifies back compatibility decoding json
 */
class UIParameterSpec extends AnyFunSuite with Matchers {

  import io.circe.syntax._
  import UIParameter.decoder

  private implicit val typingResultDecoder: Decoder[TypingResult] = Decoder.decodeJson.map(_ => typing.Unknown)

  private val expected = UIParameter("Topic", typing.Unknown, StringParameterEditor, Nil, Expression.spel(""), Map.empty, Set.empty, false)

  test("deserialize new json") {
    verify(expected.asJson.spaces2)
  }

  test("deserialize old json") {
    val json =
      """
        |{
        |    "name": "Topic",
        |    "typ" : {},
        |    "editor" : {
        |      "type" : "StringParameterEditor"
        |    },
        |    "validators": [],
        |    "defaultValue": "",
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

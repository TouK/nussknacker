package pl.touk.nussknacker.engine.test.testcase

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.JsonCodec
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.graph.expression.Expression

import java.util.UUID

// TODO: When adding multiple test cases variant, remember to validate ID and name uniqueness.
sealed trait TestCases

object TestCases {
  case class Single(value: TestCase) extends TestCases

  implicit val encoder: Encoder[TestCases] = Encoder.instance { case Single(value) =>
    Json.obj("value" -> value.asJson)
  }

  implicit val decoder: Decoder[TestCases] = Decoder.instance { cursor =>
    cursor.downField("value").as[TestCase].map(Single(_))
  }

}

@JsonCodec final case class TestCase(
    id: TestCaseId,
    name: TestCaseName,
    inputs: String,
    mocks: Map[NodeId, EnricherMock],
    assertions: Map[NodeId, List[Assertion]],
)

@JsonCodec final case class EnricherMock(expression: Expression)

@JsonCodec final case class Assertion(expression: Expression)

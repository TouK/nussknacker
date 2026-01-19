package pl.touk.nussknacker.engine.test.testcase

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
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

sealed trait Assertion

object Assertion {

  @JsonCodec final case class AssertionExpression(expression: Expression) extends Assertion

  @JsonCodec final case class PredicateAssertion(operator: AssertionOperator, expected: Expression, actual: Expression)
      extends Assertion

  implicit val assertionEncoder: Encoder[Assertion] = Encoder.instance {
    case expr: AssertionExpression => expr.asJson
    case pred: PredicateAssertion  => pred.asJson
  }

  implicit val assertionDecoder: Decoder[Assertion] = Decoder.instance { cursor =>
    if (cursor.downField("operator").failed) {
      cursor.as[AssertionExpression]
    } else {
      cursor.as[PredicateAssertion]
    }
  }

  sealed trait AssertionOperator

  object AssertionOperator {
    case object Equals extends AssertionOperator

    implicit val assertionOperatorEncoder: Encoder[AssertionOperator] = Encoder.instance { case Equals =>
      "equals".asJson
    }

    implicit val assertionOperatorDecoder: Decoder[AssertionOperator] = Decoder.instance { cursor =>
      cursor.as[String].flatMap {
        case "equals" => Right(AssertionOperator.Equals)
        case other    => Left(DecodingFailure(s"Unknown AssertionOperator: $other", cursor.history))
      }
    }

  }

}

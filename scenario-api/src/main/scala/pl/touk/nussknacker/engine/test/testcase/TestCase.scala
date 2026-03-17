package pl.touk.nussknacker.engine.test.testcase

import cats.data.NonEmptyList
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.LowerCamelcase
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.JsonCodec
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.graph.expression.Expression

final case class TestCases(list: NonEmptyList[TestCase])

object TestCases {

  implicit val encoder: Encoder[TestCases] = Encoder.instance { testCases =>
    Json.obj(
      "list" -> testCases.list.asJson,
      // Return "value" for backward compatibility
      "value" -> testCases.list.head.asJson
    )
  }

  implicit val decoder: Decoder[TestCases] = Decoder.instance { cursor =>
    val valueCursor = cursor.downField("value")
    // For backward compatibility, if "value" field is present, try to decode a single test case from "value" field
    if (valueCursor.succeeded) {
      valueCursor.as[TestCase].map(testCase => TestCases(NonEmptyList.one(testCase)))
    } else {
      cursor.downField("list").as[NonEmptyList[TestCase]].map(TestCases(_))
    }
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

  @JsonCodec final case class ExpressionAssertion(
      expression: Expression,
      description: Option[String] = None,
  ) extends Assertion

  @JsonCodec final case class PredicateAssertion(
      operator: AssertionOperator,
      expected: Expression,
      actual: Expression,
      description: Option[String] = None,
  ) extends Assertion

  implicit val assertionEncoder: Encoder[Assertion] = Encoder.instance {
    case expr: ExpressionAssertion => expr.asJson
    case pred: PredicateAssertion  => pred.asJson
  }

  implicit val assertionDecoder: Decoder[Assertion] = Decoder.instance { cursor =>
    if (cursor.downField("operator").failed) {
      cursor.as[ExpressionAssertion]
    } else {
      cursor.as[PredicateAssertion]
    }
  }

  sealed trait AssertionOperator extends EnumEntry with LowerCamelcase

  object AssertionOperator extends Enum[AssertionOperator] with CirceEnum[AssertionOperator] {
    case object Equals    extends AssertionOperator
    case object NotEquals extends AssertionOperator

    override def values: IndexedSeq[AssertionOperator] = findValues
  }

}

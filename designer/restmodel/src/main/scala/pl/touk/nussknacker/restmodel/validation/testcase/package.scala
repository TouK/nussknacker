package pl.touk.nussknacker.restmodel.validation

import cats.Semigroup
import cats.data.NonEmptyList
import cats.syntax.semigroup._
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.ErrorDetails
import pl.touk.nussknacker.engine.test.testcase.TestCaseName

package object testcase {
  type ScenarioTestCasesValidationErrors = Map[NodeId, NodeTestCasesValidationErrors]

  type NodeTestCasesValidationErrors = Map[TestCaseName, NodeTestCaseValidationErrors]
  type AssertionIndex                = Int

  @JsonCodec
  final case class NodeTestCaseValidationErrors(
      enricherMockErrors: Option[NonEmptyList[EnricherMockValidationError]],
      assertionsErrors: Option[Map[AssertionIndex, NonEmptyList[AssertionValidationError]]],
  )

  @JsonCodec
  final case class EnricherMockValidationError(
      typ: String,
      message: String,
      description: String,
      details: Option[ErrorDetails],
  )

  @JsonCodec
  final case class AssertionValidationError(
      typ: String,
      message: String,
      description: String,
      details: Option[ErrorDetails],
  )

  object ScenarioTestCasesValidationErrors {

    implicit val nodeTestCaseValidationErrorsSemigroup: Semigroup[NodeTestCaseValidationErrors] =
      Semigroup.instance { (first, second) =>
        NodeTestCaseValidationErrors(
          enricherMockErrors = first.enricherMockErrors.combine(second.enricherMockErrors),
          assertionsErrors = first.assertionsErrors.combine(second.assertionsErrors)
        )
      }

  }

}

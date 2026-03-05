package pl.touk.nussknacker.ui.api.utils

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  ValidationErrors
}

class ValidationErrorOpsSpec extends AnyFunSuite with Matchers {

  test("should use node name when provided") {
    val nodeId = NodeId("node-id-1")
    val errors = ValidationErrors.initial(
      invalidNodes = Map(
        nodeId -> List(
          NodeValidationError(
            typ = "SomeType",
            message = "Some message",
            description = "Some description",
            fieldName = None,
            errorType = NodeValidationErrorType.SaveAllowed,
            details = None
          )
        )
      )
    )

    new ValidationErrorOps.ValidationErrorOps(errors)
      .toHumanReadableMessage(Map(nodeId -> "friendly-node")) should include(
      "friendly-node (id: node-id-1): Some message"
    )
  }

}

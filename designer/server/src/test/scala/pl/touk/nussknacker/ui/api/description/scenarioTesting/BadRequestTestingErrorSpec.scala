package pl.touk.nussknacker.ui.api.description.scenarioTesting

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  ValidationErrors
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.TestingError.BadRequestTestingError.{
  badRequestTestingErrorCodec,
  SourcesCompilationError
}

class BadRequestTestingErrorSpec extends AnyFunSuite with Matchers {

  test("should serialize source compilation error with node names") {
    val nodeId = NodeId("source-id")
    val error = SourcesCompilationError(
      errors = ValidationErrors.initial(
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
      ),
      nodeNamesById = Map(nodeId -> "source-name")
    )

    badRequestTestingErrorCodec.encode(error) should include("source-name: Some message")
  }

}

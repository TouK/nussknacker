package pl.touk.nussknacker.ui.api

import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FatalUnknownError

class ScenarioTestingApiHttpServiceSpec extends AnyFunSuite with Matchers {

  test("should keep node names only for nodes with source compilation errors") {
    val nodeIdWithError = NodeId("with-error")
    val nodesWithErrors = NonEmptyList.one(
      nodeIdWithError -> NonEmptyList.one(FatalUnknownError("error", nodeId = Some(nodeIdWithError)))
    )
    val nodeNamesById = Map(
      nodeIdWithError   -> "with-error-name",
      NodeId("other-1") -> "other-1-name",
      NodeId("other-2") -> "other-2-name"
    )

    ScenarioTestingApiHttpService.filterNodeNamesById(nodesWithErrors, nodeNamesById) shouldBe Map(
      nodeIdWithError -> "with-error-name"
    )
  }

}

package pl.touk.nussknacker.ui.api

import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FatalUnknownError
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.test.testcase.Assertion.ExpressionAssertion
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.AssertionErrors
import pl.touk.nussknacker.ui.process.test.testcase.AssertionCompilationError.ExpressionAssertionCompilationError

class TestingApiErrorMessagesSpec extends AnyFunSuite with Matchers {

  test("assertion compilation message should prefer node name over node id") {
    val nodeId = NodeId("node-1")
    val assertionError = ExpressionAssertionCompilationError(
      errors = NonEmptyList.one(FatalUnknownError("boom", Some(nodeId))),
      assertion = ExpressionAssertion(Expression.spel("true")),
      nodeId = nodeId,
      nodeName = NodeName("friendlyNode")
    )

    val message = TestingApiErrorMessages.from(AssertionErrors(NonEmptyList.one(assertionError)))

    message should include("Assertion compilation error. Node: friendlyNode.")
  }

}

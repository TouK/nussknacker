package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.test.testcase.Assertion.AssertionOperator

final case class CompiledAssertions(assertions: Map[NodeId, List[CompiledAssertion]])

sealed trait CompiledAssertion

object CompiledAssertion {

  final case class CompiledExpressionAssertion(expression: CompiledExpression) extends CompiledAssertion

  final case class CompiledPredicateAssertion(
      operator: AssertionOperator,
      expected: CompiledExpression,
      actual: CompiledExpression
  ) extends CompiledAssertion

}

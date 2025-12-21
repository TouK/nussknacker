package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression

final case class CompiledAssertions(assertions: Map[NodeId, List[CompiledAssertion]])

final case class CompiledAssertion(expression: CompiledExpression)

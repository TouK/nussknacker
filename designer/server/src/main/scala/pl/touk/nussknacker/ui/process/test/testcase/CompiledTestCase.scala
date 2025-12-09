package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression

final case class CompiledTestCase(
                               id: String,
                               mocks: Map[NodeId, CompiledEnricherMock],
                               assertions: Map[NodeId, List[CompiledAssertion]],
                             )

final case class CompiledEnricherMock(expression: CompiledExpression)

final case class CompiledAssertion(expression: CompiledExpression)

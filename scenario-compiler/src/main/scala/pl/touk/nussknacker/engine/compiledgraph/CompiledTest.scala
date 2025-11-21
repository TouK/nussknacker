package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.Test.NodeName

final case class CompiledTest(
                               id: String,
                               inputs: Map[NodeName, List[CompiledTestSourceInput]],
                               mocks: Map[NodeName, CompiledEnricherMock],
                               assertions: Map[NodeName, List[CompiledAssertion]],
                             )

final case class CompiledTestSourceInput(expression: CompiledExpression)

final case class CompiledEnricherMock(expression: CompiledExpression)

final case class CompiledAssertion(expression: CompiledExpression)

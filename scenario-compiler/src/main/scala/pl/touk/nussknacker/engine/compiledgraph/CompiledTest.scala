package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.Test.NodeName
import pl.touk.nussknacker.engine.graph.TestSourceInput

final case class CompiledTest(
    name: String,
    inputs: Map[NodeName, List[TestSourceInput]],
    mocks: Map[NodeName, CompiledEnricherMock],
    assertions: Map[NodeName, List[CompiledAssertion]],
)

final case class CompiledEnricherMock(expression: CompiledExpression)

final case class CompiledAssertion(expression: CompiledExpression)

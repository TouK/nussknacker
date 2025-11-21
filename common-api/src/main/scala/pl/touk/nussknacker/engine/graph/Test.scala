package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.Test.NodeName
import pl.touk.nussknacker.engine.graph.expression.Expression

object Test {
  type NodeName = String
}

@JsonCodec final case class Test(
                                  id: String,
                                  inputs: Map[NodeName, List[TestSourceInput]], //todo: it should use new input format
                                  mocks: Map[NodeName, EnricherMock],
                                  assertions: Map[NodeName, List[Assertion]],
                                )

@JsonCodec final case class TestSourceInput(expression: Expression)

@JsonCodec final case class EnricherMock(expression: Expression)

@JsonCodec final case class Assertion(expression: String)


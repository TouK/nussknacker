package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.TestCase.NodeName
import pl.touk.nussknacker.engine.graph.expression.Expression

object TestCase {
  type NodeName = String
}

@JsonCodec final case class TestCase(
                                  id: String,
                                  inputs: String,
                                  mocks: Map[NodeName, EnricherMock],
                                  assertions: Map[NodeName, List[Assertion]],
                                )

//todo: this is meant to contain serialized CommonFormatPreliminaryScenarioRecord
@JsonCodec final case class TestSourceInput(serializedContent: String)

@JsonCodec final case class EnricherMock(expression: Expression)

@JsonCodec final case class Assertion(expression: String)


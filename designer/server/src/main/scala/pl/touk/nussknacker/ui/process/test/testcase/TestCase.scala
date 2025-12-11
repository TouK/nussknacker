package pl.touk.nussknacker.ui.process.test.testcase

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.graph.expression.Expression

import java.util.UUID


@JsonCodec final case class ScenarioWithTestCase(scenario: ScenarioGraph, testCase: TestCase)

@JsonCodec final case class TestCase(
                                      id: UUID,
                                      name: String,
                                      inputs: String,
                                      mocks: Map[NodeId, EnricherMock],
                                      assertions: Map[NodeId, List[Assertion]],
                                    )

@JsonCodec final case class TestSourceInput(serializedContent: String)

@JsonCodec final case class EnricherMock(expression: Expression)

@JsonCodec final case class Assertion(expression: Expression)


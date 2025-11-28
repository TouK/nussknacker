package pl.touk.nussknacker.ui.process.test.testcase

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.graph.expression.Expression


@JsonCodec final case class TestCase(
                                  id: String,
                                  inputs: String,
                                  mocks: Map[NodeId, EnricherMock],
                                  assertions: Map[NodeId, List[Assertion]],
                                )

//todo: this is meant to contain serialized CommonFormatPreliminaryScenarioRecord
@JsonCodec final case class TestSourceInput(serializedContent: String)

@JsonCodec final case class EnricherMock(expression: Expression)

@JsonCodec final case class Assertion(expression: String)


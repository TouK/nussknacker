package pl.touk.nussknacker.ui.process.test.testcase

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.test.testcase.TestCase

@JsonCodec final case class ScenarioWithTestCase(scenario: ScenarioGraph, testCase: TestCase)

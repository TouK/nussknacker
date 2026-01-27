package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestCaseMetadataResponseDto

class TestCaseMetadataService {

  def prepareTestCaseMetadata(
      variableTypes: Map[String, TypingResult],
      nodeData: NodeData,
  ): TestCaseMetadataResponseDto = {
    val assertionsAdditionalVariables = TestCaseVariables.getNodeVariablesTyping(variableTypes)
    TestCaseMetadataResponseDto(
      assertionsAdditionalVariables = assertionsAdditionalVariables,
      // TODO: Generate enricher mock expression based on nodeData when implemented
      enricherMockSampleExpression = None
    )
  }

}

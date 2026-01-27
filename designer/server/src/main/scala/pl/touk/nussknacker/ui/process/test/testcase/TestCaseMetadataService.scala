package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestCaseMetadataResponseDto

class TestCaseMetadataService {

  def prepareTestCaseMetadata(
      variableTypes: Map[String, TypingResult],
      nodeData: NodeData,
  ): TestCaseMetadataResponseDto = {
    TestCaseMetadataResponseDto(
      assertionsAdditionalVariables = TestCaseVariables.getNodeVariablesTyping(variableTypes),
      enricherMockSampleExpression = generateEnricherMockSampleExpression(variableTypes, nodeData)
    )
  }

  private def generateEnricherMockSampleExpression(
      variableTypes: Map[String, TypingResult],
      nodeData: NodeData
  ): Option[Expression] = {
    nodeData match {
      case enricher: Enricher =>
        for {
          outputVariableType     <- variableTypes.get(enricher.output)
          sampleExpressionString <- SpelExpressionSampleGenerator.generateSampleExpression(outputVariableType)
        } yield Expression.spel(sampleExpressionString)
      case _ =>
        None
    }
  }

}

package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.SampleEnricherMockResponseDto

class EnricherMockGenerator {

  def generateSampleExpression(
      inputVariableTypes: Map[String, TypingResult],
      enricher: Enricher,
  ): SampleEnricherMockResponseDto = {
    SampleEnricherMockResponseDto(
      enricherMockSampleExpression = generateEnricherMockSampleExpression(inputVariableTypes, enricher)
    )
  }

  private def generateEnricherMockSampleExpression(
      variableTypes: Map[String, TypingResult],
      enricher: Enricher
  ): Expression = {
        for {
          outputVariableType     <- variableTypes.get(enricher.output)
          sampleExpressionString <- SpelExpressionSampleGenerator.generateSampleExpression(outputVariableType)
        } yield Expression.spel(sampleExpressionString)
  }

}

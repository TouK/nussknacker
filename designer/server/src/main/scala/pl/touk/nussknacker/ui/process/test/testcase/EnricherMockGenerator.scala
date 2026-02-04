package pl.touk.nussknacker.ui.process.test.testcase

import cats.effect.SyncIO
import cats.effect.kernel.Resource
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.Enricher
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.GenerateEnricherMockResponseDto
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.BadRequestNodesError.EnricherMockExpressionGenerationError.{
  CompilationErrors,
  OutputVariableNotFound
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.NoContentNodesError.EnricherMockExpressionGenerationError.UnsupportedExpressionType

class EnricherMockGenerator(
    modelData: ModelData,
    engineScenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies]
) {

  private val nodeCompiler = NodeCompiler.forValidation(modelData)

  def generateExpression(
      inputVariableTypes: Map[String, TypingResult],
      enricher: Enricher,
      jobData: JobData
  ): Either[NodesError, GenerateEnricherMockResponseDto] = {
    engineScenarioCompilationDependenciesResource
      .use { engineScenarioCompilationDependencies =>
        SyncIO {
          implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
            new ScenarioCompilationDependencies(jobData, engineScenarioCompilationDependencies)

          generateExpression(inputVariableTypes, enricher, nodeCompiler)
        }
      }
      .unsafeRunSync()
  }

  private def generateExpression(
      inputVariableTypes: Map[String, TypingResult],
      enricher: Enricher,
      nodeCompiler: NodeCompiler
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Either[NodesError, GenerateEnricherMockResponseDto] = {
    val compilationResult = nodeCompiler.compileNode[Enricher](
      nodeData = enricher,
      variableTypes = inputVariableTypes,
      branchVariableTypes = None,
      outgoingEdges = Nil
    )

    compilationResult.validationContext
      .leftMap { errors =>
        CompilationErrors(errors)
      }
      .toEither
      .flatMap { validationContext =>
        val outputVariableType = validationContext.localVariables.get(enricher.output)
        outputVariableType.toRight(
          OutputVariableNotFound(enricher.output)
        )
      }
      .flatMap { typingResult =>
        SpelExpressionGenerator.generate(typingResult) match {
          case Some(sampleExpressionString) =>
            Right(
              GenerateEnricherMockResponseDto(
                enricherMockExpression = Expression.spel(sampleExpressionString)
              )
            )
          case None =>
            Left(
              UnsupportedExpressionType(typingResult)
            )
        }
      }
  }

}

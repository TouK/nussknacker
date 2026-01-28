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
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{NodesError, SampleEnricherMockResponseDto}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.BadRequestNodesError.EnricherCompilation

class EnricherMockGenerator(
    modelData: ModelData,
    engineScenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies]
) {

  private val nodeCompiler = NodeCompiler.forValidation(modelData)

  def generateSampleExpression(
      inputVariableTypes: Map[String, TypingResult],
      enricher: Enricher,
      jobData: JobData
  ): Either[NodesError, SampleEnricherMockResponseDto] = {

    engineScenarioCompilationDependenciesResource
      .use { engineScenarioCompilationDependencies =>
        SyncIO {
          implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
            new ScenarioCompilationDependencies(jobData, engineScenarioCompilationDependencies)

          generateSampleExpression(inputVariableTypes, enricher, nodeCompiler)
        }
      }
      .unsafeRunSync()
  }

  private def generateSampleExpression(
      inputVariableTypes: Map[String, TypingResult],
      enricher: Enricher,
      nodeCompiler: NodeCompiler
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Either[NodesError, SampleEnricherMockResponseDto] = {
    val compilationResult = nodeCompiler.compileNode[Enricher](
      nodeData = enricher,
      variableTypes = inputVariableTypes,
      branchVariableTypes = None,
      outgoingEdges = Nil
    )

    if (compilationResult.errors.nonEmpty) {
      Left(
        EnricherCompilation(
          enricher.id,
          compilationResult.errors.map(e => PrettyValidationErrors.formatErrorMessage(e).message)
        )
      )
    } else {
      compilationResult.validationContext.toOption.flatMap(_.localVariables.get(enricher.output)) match {
        case Some(typingResult) =>
          SpelExpressionSampleGenerator.generateSampleExpression(typingResult) match {
            case Some(sampleExpressionString) =>
              Right(
                SampleEnricherMockResponseDto(
                  enricherMockSampleExpression = Expression.spel(sampleExpressionString)
                )
              )
            case None =>
              Left(
                EnricherCompilation(
                  enricher.id,
                  List(s"Cannot generate sample expression for type: ${typingResult.display}")
                )
              )
          }
        case None =>
          Left(
            EnricherCompilation(
              enricher.id,
              List(s"Output variable '${enricher.output}' not found in enricher output")
            )
          )
      }
    }
  }

}

package pl.touk.nussknacker.ui.validation

import cats.effect.SyncIO
import cats.effect.kernel.Resource
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  NodeDataValidator,
  ValidationNotPerformed,
  ValidationPerformed
}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{NodeValidationRequest, NodeValidationResult}
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

class NodeValidator(
    modelData: ModelData,
    engineScenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies],
    fragmentRepository: FragmentRepository
) {

  def validate(processVersion: ProcessVersion, nodeData: NodeValidationRequest)(
      implicit loggedUser: LoggedUser
  ): NodeValidationResult = {
    implicit val jobData: JobData =
      JobData(nodeData.processProperties.toMetaData(processVersion.processName), processVersion)

    val nodeDataValidator = new NodeDataValidator(modelData)

    val edges = nodeData.outgoingEdges.getOrElse(Nil).map(e => OutgoingEdge(e.to, e.edgeType))

    // We create fragmentResolver for each request, because it requires LoggedUser to fetch fragments
    val fragmentResolver =
      FragmentResolver(fragmentName => fragmentRepository.fetchLatestFragmentSync(fragmentName))

    engineScenarioCompilationDependenciesResource
      .use { engineScenarioCompilationDependencies =>
        SyncIO {
          implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
            new ScenarioCompilationDependencies(jobData, engineScenarioCompilationDependencies)
          nodeDataValidator.validate(
            nodeData = nodeData.nodeData,
            variableTypes = nodeData.variableTypes,
            branchVariableTypes = nodeData.branchVariableTypes,
            outgoingEdges = edges,
            fragmentResolver = fragmentResolver
          ) match {
            case ValidationNotPerformed =>
              NodeValidationResult(
                parameters = None,
                expressionType = None,
                validationErrors = Nil,
                validationPerformed = false
              )
            case ValidationPerformed(errors, parameters, expressionType) =>
              val uiParams = parameters.map(_.map(DefinitionsService.createUIParameter))
              val uiErrors = errors.map(PrettyValidationErrors.formatErrorMessage)
              NodeValidationResult(
                parameters = uiParams,
                expressionType = expressionType,
                validationErrors = uiErrors,
                validationPerformed = true
              )
          }
        }
      }
      .unsafeRunSync()
  }

}

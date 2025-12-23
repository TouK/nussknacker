package pl.touk.nussknacker.ui.validation

import cats.effect.SyncIO
import cats.effect.kernel.Resource
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.definition.{
  AdditionalVariableProvidedInRuntime,
  EngineScenarioCompilationDependencies,
  Parameter,
  ParameterCategory,
  SpelParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
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
import pl.touk.nussknacker.ui.process.test.testcase.tests
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
              val uiParams = Some(
                createAssertionsParameter(nodeData.variableTypes) ::
                  parameters.getOrElse(Nil).map(DefinitionsService.createUIParameter)
              )
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

  // TODO: move somewhere to testcase package
  private def createAssertionsParameter(variableTypes: Map[String, TypingResult]) =
    DefinitionsService.createUIParameter(
      Parameter(
        name = ParameterName("$assertions"),
        typ = typing.Unknown,
        editors = List(SpelParameterEditor),
        validators = Nil,
        defaultValue = None,
        additionalVariables = (variableTypes ++ Map(
          "contexts" -> Typed.genericTypeClass(
            classOf[java.util.List[_]],
            List(Typed.record(variableTypes))
          ),
          "TESTS" -> Typed.fromInstance(tests)
        )).map { case (name, typ) => name -> AdditionalVariableProvidedInRuntime(typ) },
        variablesToHide = Set.empty,
        branchParam = false,
        isLazyParameter = true,
        scalaOptionParameter = false,
        javaOptionalParameter = false,
        hintText = None,
        labelOpt = None,
        category = ParameterCategory.Standard,
        changesCanReloadParameters = None,
        nonImportantForExecution = true,
        displayType = false,
      )
    )

}

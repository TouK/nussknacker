package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MissingParameters
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  NodeDataValidator,
  ValidationNotPerformed,
  ValidationPerformed
}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{NodeValidationRequest, NodeValidationResult}
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

class NodeValidator(modelData: ModelData, fragmentRepository: FragmentRepository) {

  def validate(processVersion: ProcessVersion, nodeData: NodeValidationRequest)(
      implicit loggedUser: LoggedUser
  ): NodeValidationResult = {
    implicit val jobData: JobData =
      JobData(nodeData.processProperties.toMetaData(processVersion.processName), processVersion)

    val nodeDataValidator = new NodeDataValidator(modelData)

    val validationContext = prepareValidationContext(nodeData.variableTypes)
    val branchCtxs        = nodeData.branchVariableTypes.getOrElse(Map.empty).mapValuesNow(prepareValidationContext)

    val edges = nodeData.outgoingEdges.getOrElse(Nil).map(e => OutgoingEdge(e.to, e.edgeType))

    // We create fragmentResolver for each request, because it requires LoggedUser to fetch fragments
    val fragmentResolver =
      FragmentResolver(fragmentName => fragmentRepository.fetchLatestFragmentSync(fragmentName))

    nodeDataValidator.validate(
      nodeData.nodeData,
      validationContext,
      branchCtxs,
      edges,
      fragmentResolver
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

        // We don't return MissingParameter error when we are returning those missing parameters to be added - since
        // it's not really exception ATM
        def shouldIgnoreError(pce: ProcessCompilationError): Boolean = pce match {
          case MissingParameters(params, _) =>
            params.forall(missing => uiParams.exists(_.exists(_.name == missing.value)))
          case _ => false
        }

        val uiErrors = errors.filterNot(shouldIgnoreError).map(PrettyValidationErrors.formatErrorMessage)
        NodeValidationResult(
          parameters = uiParams,
          expressionType = expressionType,
          validationErrors = uiErrors,
          validationPerformed = true
        )
    }
  }

  private def prepareValidationContext(
      variableTypes: Map[String, TypingResult]
  )(implicit jobData: JobData): ValidationContext = {
    GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
      .prepareValidationContextWithGlobalVariablesOnly(jobData)
      .copy(localVariables = variableTypes)
  }

}

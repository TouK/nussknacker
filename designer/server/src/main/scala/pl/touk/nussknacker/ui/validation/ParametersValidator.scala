package pl.touk.nussknacker.ui.validation

import cats.data.Validated.Invalid
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{ParametersValidationRequest, TestSourceParameters}

class ParametersValidator(modelData: ModelData, scenarioPropertiesNames: Iterable[String]) {

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  private val validationContextGlobalVariablesOnly =
    GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
      .prepareValidationContextWithGlobalVariablesOnly(scenarioPropertiesNames)

  def validate(
      request: ParametersValidationRequest,
  ): List[NodeValidationError] = {
    val context = validationContextGlobalVariablesOnly.copy(localVariables = request.variableTypes)
    request.parameters
      .map(param =>
        expressionCompiler.compile(param.expression, Some(ParameterName(param.name)), context, param.typ)(NodeId(""))
      )
      .collect { case Invalid(a) => a.map(PrettyValidationErrors.formatErrorMessage).toList }
      .flatten
  }

  def validate(sourceParameters: TestSourceParameters, parametersDefinition: Map[String, List[Parameter]])(
      implicit metaData: MetaData
  ): List[NodeValidationError] = {
    implicit val nodeId: NodeId   = NodeId("")
    implicit val jobData: JobData = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
    val context                   = validationContextGlobalVariablesOnly

    val parameterList: List[Parameter] =
      parametersDefinition.getOrElse(
        sourceParameters.sourceId,
        throw new IllegalStateException( // This should never happen, it would mean that API is not consistent
          s"There is no source ${sourceParameters.sourceId} associated with generated test parameters"
        )
      )

    val evaluatedParameters: List[evaluatedparam.Parameter] = sourceParameters.parameterExpressions.map {
      case (paramName, expression) => evaluatedparam.Parameter(paramName, expression)
    }.toList

    expressionCompiler
      .compileNodeParameters(
        parameterList,
        evaluatedParameters,
        nodeBranchParameters = Nil,
        context,
        branchContexts = Map.empty
      )
      .left
      .fold(List.empty[NodeValidationError])(_.map(PrettyValidationErrors.formatErrorMessage).toList)

  }

}

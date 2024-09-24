package pl.touk.nussknacker.ui.validation

import cats.data.Validated.Invalid
import cats.instances.list._
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, Validations}
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{
  AdhocTestParametersRequest,
  ParametersValidationRequest
}

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

  def validate(request: AdhocTestParametersRequest, parameters: Map[String, List[Parameter]])(
      implicit metaData: MetaData
  ): List[NodeValidationError] = {
    implicit val nodeId: NodeId = NodeId("")
    val context                 = validationContextGlobalVariablesOnly

    val parameterList: List[Parameter] =
      parameters.getOrElse(
        request.sourceParameters.sourceId,
        throw new IllegalStateException( // This should never happen, it would mean that API is not consistent
          s"There is no source ${request.sourceParameters.sourceId} associated with generated test parameters"
        )
      )

    val parameterWithExpression = parameterList.map { parameter =>
      parameter -> request.sourceParameters.parameterExpressions.getOrElse(parameter.name, parameter.finalDefaultValue)
    }

    parameterWithExpression
      .map { case (parameter, expression) =>
        val validatorsCompilationResult = parameter.validators.map { validator =>
          expressionCompiler.compileValidator(validator, parameter.name, parameter.typ, context.globalVariables)
        }.sequence

        validatorsCompilationResult.andThen { validators =>
          expressionCompiler
            .compileParam(evaluatedparam.Parameter(parameter.name, expression), context, parameter)
            .andThen(Validations.validate(validators, _))
        }
      }
      .collect { case Invalid(a) => a.map(PrettyValidationErrors.formatErrorMessage).toList }
      .flatten
  }

}

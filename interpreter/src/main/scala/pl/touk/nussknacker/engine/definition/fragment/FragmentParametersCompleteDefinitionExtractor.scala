package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated.{Invalid, Valid}
import cats.data.Writer
import cats.implicits.{catsKernelStdMonoidForList, toTraverseOps}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.FragmentParameterValidator
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.component.parameter.validator.{
  ValidatorExtractorParameters,
  ValidatorsExtractor
}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, FragmentInputDefinition}

class FragmentParametersCompleteDefinitionExtractor(
    withoutValidatorsExtractor: FragmentParametersWithoutValidatorsDefinitionExtractor,
    expressionCompiler: ExpressionCompiler
) {

  def extractParametersDefinition(
      fragmentInput: FragmentInput,
      validationContext: ValidationContext
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    val extractedParams = withoutValidatorsExtractor.extractParametersDefinition(fragmentInput)
    validateFixedValuesAndExtractValidatorsForExtractedParams(
      extractedParams,
      fragmentInput.fragmentParams.getOrElse(List.empty),
      validationContext
    )
  }

  def extractParametersDefinition(
      fragmentInputDefinition: FragmentInputDefinition,
      validationContext: ValidationContext
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    val extractedParams = withoutValidatorsExtractor.extractParametersDefinition(fragmentInputDefinition)
    validateFixedValuesAndExtractValidatorsForExtractedParams(
      extractedParams,
      fragmentInputDefinition.parameters,
      validationContext
    )
  }

  private def validateFixedValuesAndExtractValidatorsForExtractedParams(
      extractedParams: Writer[List[PartSubGraphCompilationError], List[Parameter]],
      fragmentParams: List[FragmentParameter],
      validationContext: ValidationContext,
  )(implicit nodeId: NodeId) = {

    extractedParams.mapBoth { (written, extractedParams) =>
      val paramsWithValidators =
        validateFixedValuesAndExtractValidatorsForParams(
          extractedParams,
          fragmentParams,
          validationContext
        ).sequence
      (written ++ paramsWithValidators.written, paramsWithValidators.value)
    }
  }

  private def validateFixedValuesAndExtractValidatorsForParams(
      params: List[Parameter],
      fragmentParams: List[FragmentParameter],
      validationContext: ValidationContext
  )(implicit nodeId: NodeId) = {
    val paramTypeMap     = params.map(p => p.name -> p.typ).toMap
    val fragmentParamMap = fragmentParams.map(p => p.name -> p).toMap
    val contextWithFragmentParams =
      validationContext.copy(localVariables = validationContext.localVariables ++ paramTypeMap)

    params.map(p =>
      validateFixedValuesAndExtractValidatorsForParam(
        p,
        fragmentParamMap(p.name),
        paramTypeMap,
        contextWithFragmentParams
      )
    )
  }

  private def validateFixedValuesAndExtractValidatorsForParam(
      param: Parameter,
      fragmentParameter: FragmentParameter,
      paramTypeMap: Map[String, TypingResult],
      validationContext: ValidationContext
  )(implicit nodeId: NodeId) = {
    val typ = paramTypeMap(param.name)

    val (expressionValidator, validationErrors) =
      FragmentParameterValidator.validateFixedValuesAndGetExpressionValidator(
        fragmentParameter,
        validationContext,
        expressionCompiler
      ) match {
        case Valid(validator) => (validator, List.empty)
        case Invalid(e)       => (None, e.toList)
      }

    val validators = ValidatorsExtractor
      .extract(
        ValidatorExtractorParameters(
          ParameterData(typ, Nil),
          !fragmentParameter.required,
          ParameterConfig.empty,
          param.editor
        )
      ) ++ expressionValidator

    Writer
      .value[List[PartSubGraphCompilationError], Parameter](
        param.copy(validators = validators)
      )
      .tell(validationErrors)
  }

}

object FragmentParametersCompleteDefinitionExtractor {

  def apply(
      classLoader: ClassLoader,
      expressionCompiler: ExpressionCompiler
  ): FragmentParametersCompleteDefinitionExtractor = {
    val withoutValidatorsExtractor = new FragmentParametersWithoutValidatorsDefinitionExtractor(classLoader)

    new FragmentParametersCompleteDefinitionExtractor(
      withoutValidatorsExtractor,
      expressionCompiler
    )
  }

}

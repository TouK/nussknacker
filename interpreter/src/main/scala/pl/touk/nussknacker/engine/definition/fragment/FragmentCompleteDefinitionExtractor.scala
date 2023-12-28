package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated.{Invalid, Valid}
import cats.data.Writer
import cats.implicits.{catsKernelStdMonoidForList, toTraverseOps}
import cats.instances.list._
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
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

class FragmentCompleteDefinitionExtractor(
    withoutValidatorsExtractor: FragmentWithoutValidatorsDefinitionExtractor,
    expressionCompiler: ExpressionCompiler
) {
  private val fragmentParameterValidator = new FragmentParameterValidator(expressionCompiler)

  def extractParametersDefinition(
      fragmentInput: FragmentInput,
      validationContext: ValidationContext
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    val extractedParams = withoutValidatorsExtractor.extractParametersDefinition(fragmentInput)
    validateFixedValuesAndExtractValidatorsForExtractedParams(
      extractedParams,
      fragmentInput.fragmentParams.getOrElse(List.empty),
      fragmentInput.ref.id,
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
      fragmentInputDefinition.id,
      validationContext
    )
  }

  private def validateFixedValuesAndExtractValidatorsForExtractedParams(
      extractedParams: Writer[List[PartSubGraphCompilationError], List[Parameter]],
      fragmentParams: List[FragmentParameter],
      componentName: String,
      validationContext: ValidationContext,
  )(implicit nodeId: NodeId) = {
    val componentConfig =
      withoutValidatorsExtractor.componentsUiConfig.getConfigByComponentName(componentName)

    extractedParams.mapBoth { (written, extractedParams) =>
      val paramsWithValidators =
        validateFixedValuesAndExtractValidatorsForParams(
          extractedParams,
          fragmentParams,
          componentConfig,
          validationContext
        ).sequence
      (written ++ paramsWithValidators.written, paramsWithValidators.value)
    }
  }

  private def validateFixedValuesAndExtractValidatorsForParams(
      params: List[Parameter],
      fragmentParams: List[FragmentParameter],
      componentConfig: SingleComponentConfig,
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
        componentConfig,
        contextWithFragmentParams
      )
    )
  }

  private def validateFixedValuesAndExtractValidatorsForParam(
      param: Parameter,
      fragmentParameter: FragmentParameter,
      paramTypeMap: Map[String, TypingResult],
      componentConfig: SingleComponentConfig,
      validationContext: ValidationContext
  )(implicit nodeId: NodeId) = {
    val paramConfig = componentConfig.params.flatMap(_.get(fragmentParameter.name)).getOrElse(ParameterConfig.empty)
    val typ         = paramTypeMap(param.name)

    val (expressionValidator, validationErrors) =
      fragmentParameterValidator.validateFixedValuesAndGetExpressionValidator(
        fragmentParameter,
        validationContext
      ) match {
        case Valid(validator) => (validator, List.empty)
        case Invalid(e)       => (None, e.toList)
      }

    val parameterData = ParameterData(typ, Nil)
    val isOptional    = !fragmentParameter.required

    val validators = ValidatorsExtractor
      .extract(
        ValidatorExtractorParameters(parameterData, isOptional, paramConfig, param.editor)
      ) ++ expressionValidator

    Writer
      .value[List[PartSubGraphCompilationError], Parameter](
        param.copy(validators = validators)
      )
      .tell(validationErrors)
  }

}

object FragmentCompleteDefinitionExtractor {

  def apply(
      modelConfig: Config,
      classLoader: ClassLoader,
      expressionCompiler: ExpressionCompiler
  ): FragmentCompleteDefinitionExtractor = {
    val withoutValidatorsExtractor = FragmentWithoutValidatorsDefinitionExtractor(modelConfig, classLoader)

    new FragmentCompleteDefinitionExtractor(
      withoutValidatorsExtractor,
      expressionCompiler
    )
  }

}

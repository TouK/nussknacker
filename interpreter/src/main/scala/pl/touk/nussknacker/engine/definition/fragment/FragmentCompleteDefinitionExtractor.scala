package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated.{Invalid, Valid}
import cats.data.{ValidatedNel, Writer}
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
    addExtractedValidators(
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
    addExtractedValidators(
      extractedParams,
      fragmentInputDefinition.parameters,
      validationContext
    )
  }

  private def addExtractedValidators(
      extractedParams: Writer[List[PartSubGraphCompilationError], List[Parameter]],
      fragmentParams: List[FragmentParameter],
      validationContext: ValidationContext,
  )(implicit nodeId: NodeId) = {

    extractedParams.mapBoth { (written, extractedParams) =>
      val paramsWithValidators =
        extractValidatorsToParams(extractedParams, fragmentParams, validationContext).sequence
      (written ++ paramsWithValidators.written, paramsWithValidators.value)
    }
  }

  private def extractValidatorsToParams(
      params: List[Parameter],
      fragmentParams: List[FragmentParameter],
      validationContext: ValidationContext
  )(implicit nodeId: NodeId) = {
    val paramTypeMap     = params.map(p => p.name -> p.typ).toMap
    val fragmentParamMap = fragmentParams.map(p => p.name -> p).toMap
    val contextWithFragmentParams =
      validationContext.copy(localVariables = validationContext.localVariables ++ paramTypeMap)

    params.map(p => extractValidatorsToParam(p, fragmentParamMap(p.name), paramTypeMap, contextWithFragmentParams))
  }

  private def extractValidatorsToParam(
      param: Parameter,
      fragmentParameter: FragmentParameter,
      paramTypeMap: Map[String, TypingResult],
      validationContext: ValidationContext
  )(implicit nodeId: NodeId) = {
    val typ = paramTypeMap(param.name)
    val customExpressionValidator =
      fragmentParameterValidator.validateAndGetExpressionValidator(fragmentParameter, validationContext)

    val validators = ValidatorsExtractor
      .extract(
        ValidatorExtractorParameters(
          ParameterData(typ, Nil),
          !fragmentParameter.required,
          ParameterConfig.empty,
          param.editor
        )
      ) ++ customExpressionValidator.getOrElse(List.empty)

    Writer
      .value[List[PartSubGraphCompilationError], Parameter](
        param.copy(validators = validators)
      )
      .tell(toErrorList(customExpressionValidator))
  }

  private def toErrorList(validatedNel: ValidatedNel[PartSubGraphCompilationError, _]) = validatedNel match {
    case Valid(_)   => List.empty
    case Invalid(e) => e.toList
  }

}

object FragmentCompleteDefinitionExtractor {

  def apply(
      classLoader: ClassLoader,
      expressionCompiler: ExpressionCompiler
  ): FragmentCompleteDefinitionExtractor = {
    val withoutValidatorsExtractor = new FragmentWithoutValidatorsDefinitionExtractor(classLoader)

    new FragmentCompleteDefinitionExtractor(
      withoutValidatorsExtractor,
      expressionCompiler
    )
  }

}

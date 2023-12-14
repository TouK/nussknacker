package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, ValidatedNel, Writer}
import cats.implicits.{catsKernelStdMonoidForList, toTraverseOps}
import cats.instances.list._
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FragmentParamClassLoadError
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.compile.nodecompilation.FragmentParameterValidator
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, Output}
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.{
  DefaultValueDeterminerChain,
  DefaultValueDeterminerParameters
}
import pl.touk.nussknacker.engine.definition.component.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.component.parameter.validator.{
  ValidatorExtractorParameters,
  ValidatorsExtractor
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, FragmentInputDefinition, FragmentOutputDefinition, Join}
import pl.touk.nussknacker.engine.modelconfig.{ComponentsUiConfig, ComponentsUiConfigParser}
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder

// We have two implementations of FragmentDefinitionExtractor. The only difference is that FragmentGraphDefinitionExtractor
// extract parts of definition that is used for graph resolution wheres FragmentComponentDefinitionExtractor is used
// for component definition extraction (parameters, validators, etc.) for purpose of further parameters validation
// We split it to avoid passing around ProcessingTypeData
abstract class FragmentDefinitionExtractor {

  protected def extractFragmentGraph(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, (FragmentInputDefinition, List[CanonicalNode], List[Output])] = {
    fragment.allStartNodes
      .collectFirst { case FlatNode(input: FragmentInputDefinition) :: nodes =>
        val outputs = collectOutputs(fragment)
        Valid((input, nodes, outputs))
      }
      .getOrElse(Invalid(EmptyFragmentError))
  }

  private def collectOutputs(fragment: CanonicalProcess): List[Output] = {
    fragment.collectAllNodes.collect { case FragmentOutputDefinition(_, name, fields, _) =>
      Output(name, fields.nonEmpty)
    }
  }

}

sealed trait FragmentDefinitionError

case object EmptyFragmentError extends FragmentDefinitionError

class FragmentComponentDefinitionExtractor(
    componentsUiConfig: ComponentsUiConfig,
    classLoader: ClassLoader,
    expressionCompiler: ExpressionCompiler
) extends FragmentDefinitionExtractor {

  private val fragmentParameterValidator = new FragmentParameterValidator(expressionCompiler)

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, FragmentComponentDefinition] = {
    extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val docsUrl = fragment.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      val config  = componentsUiConfig.getConfigByComponentName(fragment.id).copy(docsUrl = docsUrl)
      val parameters =
        extractFragmentParametersDefinition(fragment.id, input.parameters, None)(NodeId(input.id)).value
      val outputNames = outputs.map(_.name).sorted
      new FragmentComponentDefinition(parameters, config, outputNames)
    }
  }

  def extractParametersDefinition(
      fragmentInput: FragmentInput,
      validationContext: ValidationContext
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    val parameters = fragmentInput.fragmentParams.getOrElse(Nil)
    extractFragmentParametersDefinition(fragmentInput.ref.id, parameters, Some(validationContext))
  }

  def extractParametersDefinition(
      fragmentInputDefinition: FragmentInputDefinition,
      validationContext: Option[ValidationContext]
  ): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    extractFragmentParametersDefinition(
      fragmentInputDefinition.id,
      fragmentInputDefinition.parameters,
      validationContext
    )(
      NodeId(fragmentInputDefinition.id)
    )
  }

  private def extractFragmentParametersDefinition(
      componentId: String,
      parameters: List[FragmentParameter],
      validationContext: Option[
        ValidationContext
      ] // None can result in errors from undefined references to global variables, use only when only `value` matters
      // it can also result in ValidationExpressionParameterValidator not being properly created
  )(
      implicit nodeId: NodeId
  ): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    val config = componentsUiConfig.getConfigByComponentName(componentId)
    getParametersTypingResultsMap(parameters)
      .mapBoth { (paramsTypeMapWritten, paramsTypeMap) =>
        val ctx = validationContext
          .getOrElse(ValidationContext.empty)
          .copy(
            localVariables = paramsTypeMap
          )

        val params = parameters
          .map(param => toParameter(config, param, ctx))
          .sequence

        (paramsTypeMapWritten ++ params.written, params.value)
      }
  }

  private val nullFixedValue: FixedExpressionValue = FixedExpressionValue("", "")

  private def toParameter(
      componentConfig: SingleComponentConfig,
      fragmentParameter: FragmentParameter,
      validationContext: ValidationContext // localVariables must include this fragment's parameters
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], Parameter] = {
    val typ           = validationContext(fragmentParameter.name)
    val config        = componentConfig.params.flatMap(_.get(fragmentParameter.name)).getOrElse(ParameterConfig.empty)
    val parameterData = ParameterData(typ, Nil)
    val extractedEditor = fragmentParameter.valueEditor
      .map { valueEditor =>
        fixedValuesEditorWithAllowOtherValue(
          valueEditor.allowOtherValue,
          FixedValuesParameterEditor(
            nullFixedValue +: valueEditor.fixedValuesList.map(v => FixedExpressionValue(v.expression, v.label))
          )
        )
      }
      .getOrElse(EditorExtractor.extract(parameterData, config))

    val validation                = fragmentParameterValidator.validate(fragmentParameter, validationContext)
    val customExpressionValidator = fragmentParameterValidator.validateAndGetExpressionValidator(fragmentParameter, typ)

    val isOptional = !fragmentParameter.required

    Writer
      .value[List[PartSubGraphCompilationError], Parameter](
        Parameter
          .optional(fragmentParameter.name, typ)
          .copy(
            editor = extractedEditor,
            validators = ValidatorsExtractor
              .extract(
                ValidatorExtractorParameters(parameterData, isOptional, config, extractedEditor)
              ) ++ customExpressionValidator.getOrElse(List.empty),
            defaultValue = fragmentParameter.initialValue
              .map(i => Expression.spel(i.expression))
              .orElse(
                DefaultValueDeterminerChain.determineParameterDefaultValue(
                  DefaultValueDeterminerParameters(parameterData, isOptional, config, extractedEditor)
                )
              ),
            hintText = fragmentParameter.hintText
          )
      )
      .tell(toErrorList(List(validation, customExpressionValidator).sequence))

  }

  private def toErrorList(validatedNel: ValidatedNel[PartSubGraphCompilationError, _]) = validatedNel match {
    case Valid(_)   => List.empty
    case Invalid(e) => e.toList
  }

  private def getParametersTypingResultsMap(
      params: List[FragmentParameter]
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], Map[String, TypingResult]] =
    params
      .map(p => p.name -> getParamTypingResult(p))
      .traverse { case (paramName, writer) =>
        writer.map(typingResult => paramName -> typingResult)
      }
      .map(_.toMap)

  private def getParamTypingResult(
      fragmentParameter: FragmentParameter
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], TypingResult] =
    fragmentParameter.typ
      .toRuntimeClass(classLoader)
      .map(Typed(_))
      .map(Writer.value[List[PartSubGraphCompilationError], TypingResult])
      .getOrElse(
        Writer
          .value[List[PartSubGraphCompilationError], TypingResult](Unknown)
          .tell(
            List(FragmentParamClassLoadError(fragmentParameter.name, fragmentParameter.typ.refClazzName, nodeId.id))
          )
      )

  private def fixedValuesEditorWithAllowOtherValue(
      allowOtherValue: Boolean,
      fixedValuesEditor: SimpleParameterEditor,
  ) = {
    if (allowOtherValue) {
      Some(DualParameterEditor(fixedValuesEditor, DualEditorMode.SIMPLE))
    } else {
      Some(fixedValuesEditor)
    }
  }

}

object FragmentComponentDefinitionExtractor {

  def apply(modelData: ModelData): FragmentComponentDefinitionExtractor = {
    val expressionConfig = ModelDefinitionBuilder.empty.expressionConfig
    val expressionCompiler = ExpressionCompiler.withOptimization(
      modelData.modelClassLoader.classLoader,
      modelData.engineDictRegistry,
      expressionConfig,
      modelData.modelDefinitionWithClasses.classDefinitions
    )
    FragmentComponentDefinitionExtractor(
      modelData.modelConfig,
      modelData.modelClassLoader.classLoader,
      expressionCompiler
    )
  }

  def apply(
      modelConfig: Config,
      classLoader: ClassLoader,
      expressionCompiler: ExpressionCompiler
  ): FragmentComponentDefinitionExtractor = {
    val componentsConfig = ComponentsUiConfigParser.parse(modelConfig)
    new FragmentComponentDefinitionExtractor(
      componentsConfig,
      classLoader,
      expressionCompiler
    )
  }

}

object FragmentGraphDefinitionExtractor extends FragmentDefinitionExtractor {

  def extractFragmentGraphDefinition(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, FragmentGraphDefinition] = {
    extractFragmentGraph(fragment).map { case (input, nodes, outputs) =>
      val additionalBranches = fragment.allStartNodes.collect { case a @ FlatNode(_: Join) :: _ =>
        a
      }
      new FragmentGraphDefinition(input.parameters, nodes, additionalBranches, outputs)
    }
  }

}

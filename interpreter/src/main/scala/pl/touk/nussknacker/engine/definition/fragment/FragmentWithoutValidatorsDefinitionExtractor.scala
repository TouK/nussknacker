package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, Writer}
import cats.implicits.{catsKernelStdMonoidForList, toTraverseOps}
import cats.instances.list._
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FragmentParamClassLoadError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.ValueEditorValidator
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.{
  DefaultValueDeterminerChain,
  DefaultValueDeterminerParameters
}
import pl.touk.nussknacker.engine.definition.component.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, FragmentInputDefinition}
import pl.touk.nussknacker.engine.modelconfig.{ComponentsUiConfig, ComponentsUiConfigParser}

/*
 * This class exists as a more lightweight alternative to FragmentComponentDefinitionExtractor - it doesn't rely on ExpressionCompiler and ValidationContext
 * However, it doesn't validate the parameters' initialValue and valueEditor and doesn't extract (and validate the correctness of) the parameters' validators - use in cases where it's not needed
 */
class FragmentWithoutValidatorsDefinitionExtractor(
    val componentsUiConfig: ComponentsUiConfig,
    classLoader: ClassLoader,
) {

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, FragmentComponentDefinition] = {
    FragmentGraphDefinitionExtractor.extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val docsUrl = fragment.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      val config  = componentsUiConfig.getConfigByComponentName(fragment.id).copy(docsUrl = docsUrl)
      val parameters =
        extractFragmentParametersDefinition(fragment.id, input.parameters)(NodeId(input.id)).value
      val outputNames = outputs.map(_.name).sorted
      new FragmentComponentDefinition(parameters, config, outputNames)
    }
  }

  def extractParametersDefinition(
      fragmentInput: FragmentInput,
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    val parameters = fragmentInput.fragmentParams.getOrElse(Nil)
    extractFragmentParametersDefinition(fragmentInput.ref.id, parameters)
  }

  def extractParametersDefinition(
      fragmentInputDefinition: FragmentInputDefinition,
  ): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    extractFragmentParametersDefinition(fragmentInputDefinition.id, fragmentInputDefinition.parameters)(
      NodeId(fragmentInputDefinition.id)
    )
  }

  private def extractFragmentParametersDefinition(componentId: String, parameters: List[FragmentParameter])(
      implicit nodeId: NodeId
  ) = {
    val config = componentsUiConfig.getConfigByComponentName(componentId)
    parameters
      .map(p =>
        getParamTypingResult(p)
          .mapBoth { (written, typ) =>
            val param = toParameter(config, typ, p)
            (written ++ param.written, param.value)
          }
      )
      .sequence
  }

  private def toParameter(
      componentConfig: SingleComponentConfig,
      typ: TypingResult,
      fragmentParameter: FragmentParameter,
  )(
      implicit nodeId: NodeId
  ): Writer[List[PartSubGraphCompilationError], Parameter] = {
    val paramConfig   = componentConfig.params.flatMap(_.get(fragmentParameter.name)).getOrElse(ParameterConfig.empty)
    val parameterData = ParameterData(typ, Nil)

    val (extractedEditor, validationErrors) = fragmentParameter.valueEditor
      .map(editor =>
        ValueEditorValidator.validateAndGetEditor(
          editor,
          fragmentParameter.initialValue.map(v => FixedExpressionValue(v.expression, v.label)),
          Some(fragmentParameter.typ.refClazzName),
          fragmentParameter.name,
          Set(nodeId.id)
        ) match {
          case Valid(editor) => (Some(editor), List.empty)
          case Invalid(e)    => (None, e.toList)
        }
      )
      .getOrElse((EditorExtractor.extract(parameterData, paramConfig), List.empty))

    val isOptional = !fragmentParameter.required

    val param = Parameter
      .optional(fragmentParameter.name, typ)
      .copy(
        editor = extractedEditor,
        validators = List.empty,
        defaultValue = fragmentParameter.initialValue
          .map(i => Expression.spel(i.expression))
          .orElse(
            DefaultValueDeterminerChain.determineParameterDefaultValue(
              DefaultValueDeterminerParameters(parameterData, isOptional, paramConfig, extractedEditor)
            )
          ),
        hintText = fragmentParameter.hintText
      )

    Writer
      .value[List[PartSubGraphCompilationError], Parameter](param)
      .tell(validationErrors)
  }

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

}

object FragmentWithoutValidatorsDefinitionExtractor {

  def apply(modelData: ModelData): FragmentWithoutValidatorsDefinitionExtractor = {
    FragmentWithoutValidatorsDefinitionExtractor(
      modelData.modelConfig,
      modelData.modelClassLoader.classLoader,
    )
  }

  def apply(
      modelConfig: Config,
      classLoader: ClassLoader,
  ): FragmentWithoutValidatorsDefinitionExtractor = {
    val componentsConfig = ComponentsUiConfigParser.parse(modelConfig)
    new FragmentWithoutValidatorsDefinitionExtractor(
      componentsConfig,
      classLoader
    )
  }

}

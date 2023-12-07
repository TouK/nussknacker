package pl.touk.nussknacker.engine.definition

import cats.data.Validated.{Invalid, Valid, invalid, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel, Writer}
import cats.implicits.toTraverseOps
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  FragmentParamClassLoadError,
  MultipleOutputsForName,
  PresetIdNotFoundInProvidedPresets
}
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider.FixedValuesPreset
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, Output}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.parameter.defaults.{
  DefaultValueDeterminerChain,
  DefaultValueDeterminerParameters
}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FragmentParameter,
  ValueInputWithFixedValuesPreset,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, FragmentInputDefinition, FragmentOutputDefinition, Join}
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder

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
    componentConfig: String => Option[SingleComponentConfig],
    classLoader: ClassLoader,
    expressionCompiler: ExpressionCompiler,
    fixedValuesPresetProvider: Option[
      FixedValuesPresetProvider
    ] // in the None case, it extracts value-less FixedValuesPresetParameterEditor and no validator
) extends FragmentDefinitionExtractor {

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, FragmentComponentDefinition] = {
    extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val docsUrl = fragment.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      val config  = componentConfig(fragment.id).getOrElse(SingleComponentConfig.zero).copy(docsUrl = docsUrl)
      val fixedValuesPresets = fixedValuesPresetProvider.map(_.getAll)

      val parameters = input.parameters.map(toParameter(config, fixedValuesPresets)(_)).sequence.value
      new FragmentComponentDefinition(parameters, config, outputs)
    }
  }

  def extractParametersDefinition(
      fragmentInput: FragmentInput
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    val parameters = fragmentInput.fragmentParams.getOrElse(Nil)
    extractFragmentParametersDefinition(fragmentInput.ref.id, parameters)
  }

  def extractParametersDefinition(
      fragmentInputDefinition: FragmentInputDefinition
  ): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    extractFragmentParametersDefinition(fragmentInputDefinition.id, fragmentInputDefinition.parameters)(
      NodeId(fragmentInputDefinition.id)
    )
  }

  private def extractFragmentParametersDefinition(componentId: String, parameters: List[FragmentParameter])(
      implicit nodeId: NodeId
  ): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    val config             = componentConfig(componentId).getOrElse(SingleComponentConfig.zero)
    val fixedValuesPresets = fixedValuesPresetProvider.map(_.getAll)

    parameters
      .map(toParameter(config, fixedValuesPresets))
      .sequence
      .mapWritten(_.map(_.toError(nodeId.id)))
  }

  private def toParameter(
      componentConfig: SingleComponentConfig,
      fixedValuesPresets: Option[Map[String, FixedValuesPreset]]
  )(
      fragmentParameter: FragmentParameter
  ): Writer[List[FragmentParameterErrorData], Parameter] = {
    fragmentParameter.typ
      .toRuntimeClass(classLoader)
      .map(Typed(_))
      .map(Writer.value[List[FragmentParamClassLoadErrorData], TypingResult])
      .getOrElse(
        Writer
          .value[List[FragmentParamClassLoadErrorData], TypingResult](Unknown)
          .tell(List(FragmentParamClassLoadErrorData(fragmentParameter.name, fragmentParameter.typ.refClazzName)))
      )
      .mapBoth { (written, value) =>
        val parameter = toParameter(componentConfig, value, fragmentParameter, fixedValuesPresets)
        (written ++ parameter.written, parameter.value)
      }
  }

  private val nullFixedValue: FixedExpressionValue = FixedExpressionValue("", "")

  private def extractEditor(
      fragmentParameter: FragmentParameter,
      config: ParameterConfig,
      parameterData: ParameterData,
      fixedValuesPresets: Option[Map[String, FixedValuesPreset]]
  ) =
    fragmentParameter.valueEditor match {
      case Some(ValueInputWithFixedValuesPreset(presetId, allowOtherValue)) =>
        val (errors, fixedValues) = fixedValuesPresets
          .map { presets =>
            presets.get(presetId) match {
              case Some(preset) => (List.empty, Some(nullFixedValue +: preset.values))
              case None =>
                (List(PresetIdNotFoundInProvidedPresetsErrorData(fragmentParameter.name, presetId)), None)
            }
          }
          .getOrElse((List.empty, None))

        Writer(
          errors,
          fixedValuesEditorWithAllowOtherValue(
            allowOtherValue,
            FixedValuesPresetParameterEditor(
              presetId,
              fixedValues
            )
          )
        )

      case Some(ValueInputWithFixedValuesProvided(fixedValuesList, allowOtherValue)) =>
        Writer(
          List.empty,
          fixedValuesEditorWithAllowOtherValue(
            allowOtherValue,
            FixedValuesParameterEditor(
              nullFixedValue +: fixedValuesList.map(v => FixedExpressionValue(v.expression, v.label))
            )
          )
        )

      case None => Writer(List.empty, EditorExtractor.extract(parameterData, config))
    }

  private def toParameter(
      componentConfig: SingleComponentConfig,
      typ: typing.TypingResult,
      fragmentParameter: FragmentParameter,
      fixedValuesPresets: Option[Map[String, FixedValuesPreset]]
  ): Writer[List[PresetIdNotFoundInProvidedPresetsErrorData], Parameter] = {
    val config        = componentConfig.params.flatMap(_.get(fragmentParameter.name)).getOrElse(ParameterConfig.empty)
    val parameterData = ParameterData(typ, Nil)

    val editor = extractEditor(fragmentParameter, config, parameterData, fixedValuesPresets)

    val isOptional = !fragmentParameter.required
    Writer(
      editor.written,
      Parameter
        .optional(fragmentParameter.name, typ)
        .copy(
          editor = editor.value,
          validators = ValidatorsExtractor
            .extract(
              ValidatorExtractorParameters(parameterData, isOptional, config, editor.value)
            ),
          defaultValue = fragmentParameter.initialValue
            .map(i => Expression.spel(i.expression))
            .orElse(
              DefaultValueDeterminerChain.determineParameterDefaultValue(
                DefaultValueDeterminerParameters(parameterData, isOptional, config, editor.value)
              )
            ),
          hintText = fragmentParameter.hintText
        )
    )
  }

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

  def apply(
      modelData: ModelData,
      fixedValuesPresetProvider: Option[FixedValuesPresetProvider]
  ): FragmentComponentDefinitionExtractor = {
    val expressionConfig = ProcessDefinitionBuilder.empty.expressionConfig
    val expressionCompiler = ExpressionCompiler.withOptimization(
      modelData.modelClassLoader.classLoader,
      modelData.engineDictRegistry,
      expressionConfig,
      modelData.modelDefinitionWithTypes.typeDefinitions
    )
    FragmentComponentDefinitionExtractor(
      modelData.processConfig,
      modelData.modelClassLoader.classLoader,
      expressionCompiler,
      fixedValuesPresetProvider
    )
  }

  def apply(
      processConfig: Config,
      classLoader: ClassLoader,
      expressionCompiler: ExpressionCompiler,
      fixedValuesPresetProvider: Option[FixedValuesPresetProvider]
  ): FragmentComponentDefinitionExtractor = {
    val componentsConfig = ComponentsUiConfigExtractor.extract(processConfig)

    new FragmentComponentDefinitionExtractor(
      componentsConfig.get,
      classLoader,
      expressionCompiler,
      fixedValuesPresetProvider
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

class FragmentComponentDefinition(
    val parameters: List[Parameter],
    val config: SingleComponentConfig,
    allOutputs: List[Output]
) {
  def outputNames: List[String] = allOutputs.map(_.name).sorted
}

class FragmentGraphDefinition(
    val fragmentParameters: List[FragmentParameter],
    val nodes: List[CanonicalNode],
    val additionalBranches: List[List[CanonicalNode]],
    allOutputs: List[Output]
) {

  def validOutputs(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Set[Output]] = {
    NonEmptyList.fromList(allOutputs.groupBy(_.name).filter(_._2.size > 1).toList) match {
      case Some(groups) => invalid(groups.map(gr => MultipleOutputsForName(gr._1, nodeId.id)))
      case None         => valid(allOutputs.toSet)
    }
  }

}

sealed trait FragmentParameterErrorData {
  def toError(nodeId: String): PartSubGraphCompilationError
}

case class FragmentParamClassLoadErrorData(fieldName: String, refClazzName: String) extends FragmentParameterErrorData {
  override def toError(nodeId: String): FragmentParamClassLoadError =
    FragmentParamClassLoadError(fieldName, refClazzName, nodeId)
}

case class PresetIdNotFoundInProvidedPresetsErrorData(fieldName: String, presetId: String)
    extends FragmentParameterErrorData {
  override def toError(nodeId: String): PresetIdNotFoundInProvidedPresets =
    PresetIdNotFoundInProvidedPresets(fieldName, presetId, nodeId)
}

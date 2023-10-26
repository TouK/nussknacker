package pl.touk.nussknacker.engine.definition

import cats.data.Validated.{Invalid, Valid, invalid, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel, Writer}
import cats.implicits.toTraverseOps
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  FragmentParamClassLoadError,
  MultipleOutputsForName
}
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.definition.{
  DualParameterEditor,
  FixedExpressionValue,
  FixedValuesParameterEditor,
  FixedValuesPresetParameterEditor,
  Parameter
}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.compile.Output
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.parameter.defaults.{
  DefaultValueDeterminerChain,
  DefaultValueDeterminerParameters
}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentParameter, FragmentParameterInputMode}
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, FragmentInputDefinition, FragmentOutputDefinition, Join}

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
    classLoader: ClassLoader
) extends FragmentDefinitionExtractor {

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, FragmentComponentDefinition] = {
    extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val docsUrl    = fragment.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      val config     = componentConfig(fragment.id).getOrElse(SingleComponentConfig.zero).copy(docsUrl = docsUrl)
      val parameters = input.parameters.map(toParameter(config)(_)).sequence.value
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
    val config = componentConfig(componentId).getOrElse(SingleComponentConfig.zero)
    parameters
      .map(toParameter(config))
      .sequence
      .mapWritten(_.map(data => FragmentParamClassLoadError(data.fieldName, data.refClazzName, nodeId.id)))
  }

  private def toParameter(
      componentConfig: SingleComponentConfig
  )(fragmentParameter: FragmentParameter): Writer[List[FragmentParamClassLoadErrorData], Parameter] = {
    fragmentParameter.typ
      .toRuntimeClass(classLoader)
      .map(Typed(_))
      .map(Writer.value[List[FragmentParamClassLoadErrorData], TypingResult])
      .getOrElse(
        Writer
          .value[List[FragmentParamClassLoadErrorData], TypingResult](Unknown)
          .tell(List(FragmentParamClassLoadErrorData(fragmentParameter.name, fragmentParameter.typ.refClazzName)))
      )
      .map(toParameter(componentConfig, _, fragmentParameter))
  }

  private def toParameter(
      componentConfig: SingleComponentConfig,
      typ: typing.TypingResult,
      fragmentParameter: FragmentParameter
  ) = {
    val config        = componentConfig.params.flatMap(_.get(fragmentParameter.name)).getOrElse(ParameterConfig.empty)
    val parameterData = ParameterData(typ, Nil)
    val extractedEditor = if (fragmentParameter.effectiveFixedValuesList.nonEmpty) {
      val fixedValuesEditor = FixedValuesParameterEditor(
        fragmentParameter.effectiveFixedValuesList.map(v => FixedExpressionValue(v.expression, v.label))
      )

      if (fragmentParameter.inputMode == FragmentParameterInputMode.InputModeFixedList)
        Some(fixedValuesEditor)
      else
        Some(DualParameterEditor(fixedValuesEditor, DualEditorMode.SIMPLE))
    } else
      EditorExtractor.extract(parameterData, config)

    val isOptional = !fragmentParameter.required
    Parameter
      .optional(fragmentParameter.name, typ)
      .copy(
        editor = extractedEditor,
        validators = ValidatorsExtractor
          .extract(ValidatorExtractorParameters(parameterData, isOptional = isOptional, config, extractedEditor)),
        defaultValue = fragmentParameter.initialValue
          .map(i => Expression.spel(i.expression))
          .orElse(
            DefaultValueDeterminerChain.determineParameterDefaultValue(
              DefaultValueDeterminerParameters(parameterData, isOptional = isOptional, config, extractedEditor)
            )
          ),
        hintText = fragmentParameter.hintText
      )
  }

}

object FragmentComponentDefinitionExtractor {

  def apply(modelData: ModelData): FragmentComponentDefinitionExtractor = {
    FragmentComponentDefinitionExtractor(modelData.processConfig, modelData.modelClassLoader.classLoader)
  }

  def apply(modelConfig: Config, classLoader: ClassLoader): FragmentComponentDefinitionExtractor = {
    val componentsConfig = ComponentsUiConfigExtractor.extract(modelConfig)
    new FragmentComponentDefinitionExtractor(componentsConfig.get, classLoader)
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

case class FragmentParamClassLoadErrorData(fieldName: String, refClazzName: String)

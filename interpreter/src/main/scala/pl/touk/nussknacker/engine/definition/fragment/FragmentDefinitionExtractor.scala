package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, Writer}
import cats.implicits.toTraverseOps
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FragmentParamClassLoadError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
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
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
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
    componentConfig: String => Option[SingleComponentConfig],
    classLoader: ClassLoader,
    expressionCompiler: ExpressionCompiler,
) extends FragmentDefinitionExtractor {

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, FragmentComponentDefinition] = {
    extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val docsUrl     = fragment.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      val config      = componentConfig(fragment.id).getOrElse(SingleComponentConfig.zero).copy(docsUrl = docsUrl)
      val parameters  = input.parameters.map(toParameter(config)(_)(NodeId(input.id))).sequence.value
      val outputNames = outputs.map(_.name).sorted
      new FragmentComponentDefinition(parameters, config, outputNames)
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
  )(
      fragmentParameter: FragmentParameter
  )(implicit nodeId: NodeId): Writer[List[FragmentParamClassLoadErrorData], Parameter] = {
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

  private val nullFixedValue: FixedExpressionValue = FixedExpressionValue("", "")

  private def toParameter(
      componentConfig: SingleComponentConfig,
      typ: typing.TypingResult,
      fragmentParameter: FragmentParameter
  )(implicit nodeId: NodeId): Parameter = {
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

    val customExpressionValidator = fragmentParameter.valueCompileTimeValidation.flatMap(expr => {
      expressionCompiler
        .compileWithoutContextValidation(expr.validationExpression, fragmentParameter.name, Typed[Boolean])
        .toOption
        .map { expression =>
          ValidationExpressionParameterValidator(
            expression,
            fragmentParameter.valueCompileTimeValidation.flatMap(_.validationFailedMessage)
          )
        }
    })

    val isOptional = !fragmentParameter.required

    Parameter
      .optional(fragmentParameter.name, typ)
      .copy(
        editor = extractedEditor,
        validators = ValidatorsExtractor
          .extract(
            ValidatorExtractorParameters(parameterData, isOptional, config, extractedEditor)
          ) ++ customExpressionValidator,
        defaultValue = fragmentParameter.initialValue
          .map(i => Expression.spel(i.expression))
          .orElse(
            DefaultValueDeterminerChain.determineParameterDefaultValue(
              DefaultValueDeterminerParameters(parameterData, isOptional, config, extractedEditor)
            )
          ),
        hintText = fragmentParameter.hintText
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
      componentsConfig.get,
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

case class FragmentParamClassLoadErrorData(fieldName: String, refClazzName: String)

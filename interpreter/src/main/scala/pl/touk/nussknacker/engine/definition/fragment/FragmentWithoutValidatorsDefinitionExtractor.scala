package pl.touk.nussknacker.engine.definition.fragment

import cats.data.{Validated, Writer}
import cats.implicits.{catsKernelStdMonoidForList, toTraverseOps}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FragmentParamClassLoadError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.{
  DefaultValueDeterminerChain,
  DefaultValueDeterminerParameters
}
import pl.touk.nussknacker.engine.definition.component.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, FragmentInputDefinition}

/*
 * This class exists as a more lightweight alternative to FragmentComponentDefinitionExtractor - it doesn't rely on ExpressionCompiler and ValidationContext
 * However, it doesn't extract (and validate the correctness of) the parameters' validators - use in cases where it's not needed
 */
class FragmentWithoutValidatorsDefinitionExtractor(
    classLoader: ClassLoader
) {

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess
  ): Validated[FragmentDefinitionError, FragmentComponentDefinition] = {
    FragmentGraphDefinitionExtractor.extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val parameters =
        extractFragmentParametersDefinition(input.parameters)(NodeId(input.id)).value
      val outputNames = outputs.map(_.name).sorted
      val docsUrl     = fragment.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      new FragmentComponentDefinition(parameters, outputNames, docsUrl)
    }
  }

  def extractParametersDefinition(
      fragmentInput: FragmentInput,
  )(implicit nodeId: NodeId): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    val parameters = fragmentInput.fragmentParams.getOrElse(Nil)
    extractFragmentParametersDefinition(parameters)
  }

  def extractParametersDefinition(
      fragmentInputDefinition: FragmentInputDefinition,
  ): Writer[List[PartSubGraphCompilationError], List[Parameter]] = {
    extractFragmentParametersDefinition(fragmentInputDefinition.parameters)(
      NodeId(fragmentInputDefinition.id)
    )
  }

  private def extractFragmentParametersDefinition(parameters: List[FragmentParameter])(
      implicit nodeId: NodeId
  ) = {
    parameters.map(p => getParamTypingResult(p).map(toParameter(_, p))).sequence
  }

  private val nullFixedValue: FixedExpressionValue = FixedExpressionValue("", "")

  private def toParameter(
      typ: TypingResult,
      fragmentParameter: FragmentParameter,
  ) = {
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
      .getOrElse(EditorExtractor.extract(parameterData, ParameterConfig.empty))

    Parameter
      .optional(fragmentParameter.name, typ)
      .copy(
        editor = extractedEditor,
        validators = List.empty,
        defaultValue = fragmentParameter.initialValue
          .map(i => Expression.spel(i.expression))
          .orElse(
            DefaultValueDeterminerChain.determineParameterDefaultValue(
              DefaultValueDeterminerParameters(
                parameterData,
                !fragmentParameter.required,
                ParameterConfig.empty,
                extractedEditor
              )
            )
          ),
        hintText = fragmentParameter.hintText
      )
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

package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, Writer}
import cats.implicits.{catsKernelStdMonoidForList, toTraverseOps}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentInfo, ComponentType, ParameterConfig}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FragmentParamClassLoadError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.FragmentParameterValidator
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
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
 * However, it doesn't validate the parameters' initialValue and valueEditor and doesn't extract (and validate the correctness of) the parameters' validators - use in cases where it's not needed
 */
class FragmentWithoutValidatorsDefinitionExtractor(classLoader: ClassLoader) {

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess,
      componentInfoToId: ComponentInfo => ComponentId
  ): Validated[FragmentDefinitionError, ComponentStaticDefinition] = {
    FragmentGraphDefinitionExtractor.extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val parameters =
        extractFragmentParametersDefinition(input.parameters)(NodeId(input.id)).value
      val outputNames = outputs.map(_.name).sorted
      val docsUrl     = fragment.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      val componentId = componentInfoToId(ComponentInfo(ComponentType.Fragment, fragment.name.value))

      FragmentComponentDefinition(Some(componentId), parameters, outputNames, docsUrl)
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
    parameters
      .map(p =>
        getParamTypingResult(p)
          .mapBoth { (written, typ) =>
            val param = toParameter(typ, p)
            (written ++ param.written, param.value)
          }
      )
      .sequence
  }

  private def toParameter(
      typ: TypingResult,
      fragmentParameter: FragmentParameter,
  )(
      implicit nodeId: NodeId
  ): Writer[List[PartSubGraphCompilationError], Parameter] = {
    val parameterData = ParameterData(typ, Nil)

    val (extractedEditor, validationErrors) = fragmentParameter.valueEditor
      .map(editor =>
        FragmentParameterValidator.validateAgainstClazzRefAndGetEditor(
          valueEditor = editor,
          initialValue = fragmentParameter.initialValue,
          refClazz = fragmentParameter.typ,
          paramName = fragmentParameter.name,
          nodeIds = Set(nodeId.id)
        ) match {
          case Valid(editor) => (Some(editor), List.empty)
          case Invalid(e)    => (None, e.toList)
        }
      )
      .getOrElse((EditorExtractor.extract(parameterData, ParameterConfig.empty), List.empty))

    val param = Parameter
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

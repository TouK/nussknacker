package pl.touk.nussknacker.engine.definition.fragment

import cats.Id
import cats.data.Validated.{Invalid, Valid}
import cats.data.{Writer, WriterT}
import cats.implicits.{catsKernelStdMonoidForList, toTraverseOps}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FragmentParamClassLoadError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.nodecompilation.FragmentParameterValidator
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
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, FragmentInputDefinition}

/*
 * This class doesn't validate the parameters' initialValue and valueEditor (e.g. values can be of incorrect type), as it would require ExpressionCompiler and ValidationContext.
 * They are validated separately when creating fragment in NodeCompiler.compileSource, but if they are not validated it is not a breaking issue anyway as a process using these incorrect values will fail validation.
 */
class FragmentParametersDefinitionExtractor(classLoader: ClassLoader) {

  def extractParametersDefinition(
      fragmentInput: FragmentInput
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

  def extractFragmentParametersDefinition(parameters: List[FragmentParameter])(
      implicit nodeId: NodeId
  ): WriterT[Id, List[PartSubGraphCompilationError], List[Id[Parameter]]] = {
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

    val validationExpressionValidator = fragmentParameter.valueCompileTimeValidation.map(validation =>
      ValidationExpressionParameterValidatorToCompile(validation)
    )

    val validators = validationExpressionValidator ++ ValidatorsExtractor
      .extract(
        ValidatorExtractorParameters(
          ParameterData(typ, Nil),
          !fragmentParameter.required,
          ParameterConfig.empty,
          extractedEditor
        )
      )

    val param = Parameter
      .optional(fragmentParameter.name, typ)
      .copy(
        editor = extractedEditor,
        validators = validators.toList,
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

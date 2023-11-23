package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  InvalidFixedValuesType,
  MissingFixedValuesList,
  MissingFixedValuesPresetId
}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FixedValuesType.{Preset, UserDefinedList}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.ParameterInputMode.{
  InputModeAny,
  InputModeAnyWithSuggestions,
  InputModeFixedList
}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue,
  ParameterInputConfig,
  ParameterInputMode
}

sealed trait ParameterInputConfigResolved

case object ParameterInputConfigResolvedAny extends ParameterInputConfigResolved

case class ParameterInputConfigResolvedUserDefinedList(
    inputMode: ParameterInputMode.Value,
    fixedValuesList: List[FixedExpressionValue]
) extends ParameterInputConfigResolved

case class ParameterInputConfigResolvedPreset(
    inputMode: ParameterInputMode.Value,
    fixedValuesListPresetId: String,
    resolvedPresetFixedValuesList: Option[List[FixedExpressionValue]]
) extends ParameterInputConfigResolved

object ParameterInputConfigResolved {

  def resolveInputConfigIgnoreValidation(
      inputConfig: ParameterInputConfig,
  ): Option[ParameterInputConfigResolved] = {
    resolveInputConfig(inputConfig, "", "").toOption
  }

  def resolveInputConfig(
      inputConfig: ParameterInputConfig,
      parameterName: String,
      nodeId: String
  ): ValidatedNel[ProcessCompilationError, ParameterInputConfigResolved] =
    inputConfig.inputMode match {
      case InputModeAny => Valid(ParameterInputConfigResolvedAny)
      case InputModeAnyWithSuggestions | InputModeFixedList =>
        inputConfig.fixedValuesType match {
          case Some(UserDefinedList) =>
            inputConfig.fixedValuesList
              .map(fixedValues =>
                Valid(ParameterInputConfigResolvedUserDefinedList(inputConfig.inputMode, fixedValues))
              )
              .getOrElse(Invalid(NonEmptyList.of(MissingFixedValuesList(parameterName, Set(nodeId)))))
          case Some(Preset) =>
            inputConfig.fixedValuesListPresetId
              .map(presetId =>
                Valid(
                  ParameterInputConfigResolvedPreset(
                    inputConfig.inputMode,
                    presetId,
                    inputConfig.resolvedPresetFixedValuesList
                  )
                )
              )
              .getOrElse(Invalid(NonEmptyList.of(MissingFixedValuesPresetId(parameterName, Set(nodeId)))))
          case _ =>
            Invalid(
              NonEmptyList.of(
                InvalidFixedValuesType(
                  parameterName,
                  inputConfig.fixedValuesType.map(_.toString),
                  inputConfig.inputMode.toString,
                  Set(nodeId)
                )
              )
            )
        }
    }

}

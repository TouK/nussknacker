package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{MetaData, VariableConstants}
import pl.touk.nussknacker.engine.definition.globalvariables.{
  ExpressionConfigDefinition,
  GlobalVariableDefinitionWithValue,
  ValueWithType
}
import pl.touk.nussknacker.engine.util.Implicits._

class GlobalVariablesPreparer(
    globalVariablesDefWithObject: Map[String, GlobalVariableDefinitionWithValue],
    hideMetaVariable: Boolean
) {

  def prepareValidationContextWithGlobalVariablesOnly(metaData: MetaData): ValidationContext =
    ValidationContext(
      localVariables = Map.empty,
      globalVariables = prepareGlobalVariables(metaData).mapValuesNow(_.typ)
    )

  def prepareValidationContextWithGlobalVariablesOnly(
      scenarioPropertiesNames: Iterable[String]
  ): ValidationContext = ValidationContext(
    localVariables = Map.empty,
    globalVariables = prepareGlobalVariablesTypes(scenarioPropertiesNames)
  )

  def prepareGlobalVariables(metaData: MetaData): Map[String, ValueWithType] = {
    val globalVariablesWithType = globalVariablesDefWithObject.mapValuesNow(_.valueWithType(metaData))
    if (hideMetaVariable) {
      globalVariablesWithType
    } else {
      globalVariablesWithType + (VariableConstants.MetaVariableName -> MetaVariables.withType(metaData))
    }
  }

  private def prepareGlobalVariablesTypes(scenarioPropertiesNames: Iterable[String]): Map[String, TypingResult] = {
    val globalVariableTypes = globalVariablesDefWithObject.mapValuesNow(_.typ)
    if (hideMetaVariable) {
      globalVariableTypes
    } else {
      globalVariableTypes + (VariableConstants.MetaVariableName -> MetaVariables.typingResult(
        scenarioPropertiesNames
      ))
    }
  }

}

object GlobalVariablesPreparer {

  def apply(expressionDefinition: ExpressionConfigDefinition): GlobalVariablesPreparer = {
    new GlobalVariablesPreparer(expressionDefinition.globalVariables, expressionDefinition.hideMetaVariable)
  }

}

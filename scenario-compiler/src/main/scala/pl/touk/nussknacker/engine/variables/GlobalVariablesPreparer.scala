package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{JobData, MetaData, VariableConstants}
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.{
  ExpressionConfigDefinition,
  GlobalVariableDefinitionWithImplementation,
  ObjectWithType
}
import pl.touk.nussknacker.engine.util.Implicits._

class GlobalVariablesPreparer(
    globalVariablesDefWithImpl: Map[String, GlobalVariableDefinitionWithImplementation],
    hideMetaVariable: Boolean
) {

  def prepareValidationContextWithGlobalVariablesOnly(jobData: JobData): ValidationContext =
    ValidationContext(
      localVariables = Map.empty,
      globalVariables = prepareGlobalVariables(jobData).mapValuesNow(_.typ)
    )

  def prepareValidationContextWithGlobalVariablesOnly(
      scenarioPropertiesNames: Iterable[String]
  ): ValidationContext = ValidationContext(
    localVariables = Map.empty,
    globalVariables = prepareGlobalVariablesTypes(scenarioPropertiesNames)
  )

  def prepareGlobalVariables(jobData: JobData): Map[String, ObjectWithType] = {
    val globalVariablesWithType = globalVariablesDefWithImpl.mapValuesNow(_.objectWithType(jobData.metaData))
    if (hideMetaVariable) {
      globalVariablesWithType
    } else {
      globalVariablesWithType + (VariableConstants.MetaVariableName -> MetaVariables.withType(jobData))
    }
  }

  private def prepareGlobalVariablesTypes(scenarioPropertiesNames: Iterable[String]): Map[String, TypingResult] = {
    val globalVariableTypes = globalVariablesDefWithImpl.mapValuesNow(_.typ)
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

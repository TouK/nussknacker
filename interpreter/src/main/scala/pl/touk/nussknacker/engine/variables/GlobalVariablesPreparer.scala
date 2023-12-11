package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{MetaData, VariableConstants}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.util.Implicits._

// TODO: Types part and implementation part should be separated
class GlobalVariablesPreparer(
    globalVariablesDefWithImpl: Map[String, ComponentDefinitionWithImplementation],
    hideMetaVariable: Boolean
) {

  def emptyLocalVariablesValidationContext(metaData: MetaData): ValidationContext =
    validationContextWithLocalVariables(metaData, Map.empty)

  def validationContextWithLocalVariables(
      metaData: MetaData,
      localVariables: Map[String, TypingResult]
  ): ValidationContext = ValidationContext(
    localVariables,
    prepareGlobalVariables(metaData).mapValuesNow(_.typ)
  )

  def emptyLocalVariablesValidationContext(
      scenarioPropertiesNames: Iterable[String]
  ): ValidationContext = ValidationContext(
    Map.empty,
    prepareGlobalVariablesTypes(scenarioPropertiesNames)
  )

  def prepareGlobalVariables(metaData: MetaData): Map[String, ObjectWithType] = {
    val globalVariablesWithType = globalVariablesDefWithImpl.mapValuesNow(toGlobalVariable(_, metaData))
    if (hideMetaVariable) {
      globalVariablesWithType
    } else {
      globalVariablesWithType + (VariableConstants.MetaVariableName -> MetaVariables.withType(metaData))
    }
  }

  private def prepareGlobalVariablesTypes(scenarioPropertiesNames: Iterable[String]): Map[String, TypingResult] = {
    val globalVariablesWithType = globalVariablesDefWithImpl.mapValuesNow(toGlobalVariableType)
    if (hideMetaVariable) {
      globalVariablesWithType
    } else {
      globalVariablesWithType + (VariableConstants.MetaVariableName -> MetaVariables.typingResult(
        scenarioPropertiesNames
      ))
    }
  }

  private def toGlobalVariable(
      componentDefWithImpl: ComponentDefinitionWithImplementation,
      metaData: MetaData
  ): ObjectWithType = {
    componentDefWithImpl.implementation match {
      case typedGlobalVariable: TypedGlobalVariable =>
        ObjectWithType(typedGlobalVariable.value(metaData), typedGlobalVariable.returnType(metaData))
      case _ =>
        ObjectWithType(
          componentDefWithImpl.implementation,
          componentDefWithImpl.returnType.getOrElse(
            throw new IllegalStateException("Global variable with empty return type.")
          )
        )
    }
  }

  private def toGlobalVariableType(componentDefWithImpl: ComponentDefinitionWithImplementation): TypingResult = {
    componentDefWithImpl.implementation match {
      case typedGlobalVariable: TypedGlobalVariable =>
        typedGlobalVariable.initialReturnType
      case _ =>
        componentDefWithImpl.returnType.getOrElse(
          throw new IllegalStateException("Global variable with empty return type.")
        )
    }
  }

}

object GlobalVariablesPreparer {

  def apply(
      expressionDefinition: ExpressionConfigDefinition[ComponentDefinitionWithImplementation]
  ): GlobalVariablesPreparer = {
    new GlobalVariablesPreparer(expressionDefinition.globalVariables, expressionDefinition.hideMetaVariable)
  }

}

case class ObjectWithType(obj: Any, typ: TypingResult)

package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{MetaData, VariableConstants}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.util.Implicits._

class GlobalVariablesPreparer(
    globalVariablesDefWithImpl: Map[String, MethodBasedComponentDefinitionWithLogic],
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

  def prepareGlobalVariables(metaData: MetaData): Map[String, ObjectWithType] = {
    val globalVariablesWithType = globalVariablesDefWithImpl.mapValuesNow(toGlobalVariable(_, metaData))
    if (hideMetaVariable) {
      globalVariablesWithType
    } else {
      globalVariablesWithType + (VariableConstants.MetaVariableName -> MetaVariables.withType(metaData))
    }
  }

  private def prepareGlobalVariablesTypes(scenarioPropertiesNames: Iterable[String]): Map[String, TypingResult] = {
    val globalVariableTypes = globalVariablesDefWithImpl.mapValuesNow(toGlobalVariableType)
    if (hideMetaVariable) {
      globalVariableTypes
    } else {
      globalVariableTypes + (VariableConstants.MetaVariableName -> MetaVariables.typingResult(
        scenarioPropertiesNames
      ))
    }
  }

  private def toGlobalVariable(
      componentDefWithImpl: MethodBasedComponentDefinitionWithLogic,
      metaData: MetaData
  ): ObjectWithType = {
    componentDefWithImpl.component match {
      case typedGlobalVariable: TypedGlobalVariable =>
        ObjectWithType(typedGlobalVariable.value(metaData), typedGlobalVariable.returnType(metaData))
      case _ =>
        ObjectWithType(
          componentDefWithImpl.component,
          componentDefWithImpl.returnType
            .getOrElse(
              throw new IllegalStateException("Global variable with empty return type.")
            )
        )
    }
  }

  private def toGlobalVariableType(
      componentDefWithImpl: MethodBasedComponentDefinitionWithLogic
  ): TypingResult = {
    componentDefWithImpl.component match {
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
      expressionDefinition: ExpressionConfigDefinition[ComponentDefinitionWithLogic]
  ): GlobalVariablesPreparer = {
    // We have an assumption that GlobalVariables are handled by MethodBasedComponentDefinitionWithImplementation
    // See GlobalVariableDefinitionExtractor
    val methodBasedGlobalVariables = expressionDefinition.globalVariables.mapValuesNow {
      case methodBased: MethodBasedComponentDefinitionWithLogic => methodBased
      case dynamic: DynamicComponentDefinitionWithLogic =>
        throw new IllegalStateException(s"Global variable represented as a dynamic component: $dynamic")
    }
    new GlobalVariablesPreparer(methodBasedGlobalVariables, expressionDefinition.hideMetaVariable)
  }

}

case class ObjectWithType(obj: Any, typ: TypingResult)

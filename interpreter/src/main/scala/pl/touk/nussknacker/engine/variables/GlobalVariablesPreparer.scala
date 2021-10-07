package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.{VariableConstants, MetaData}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, ObjectWithType}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.util.Implicits._

class GlobalVariablesPreparer(globalVariablesWithMethodDef: Map[String, ObjectWithMethodDef], hideMetaVariable: Boolean) {

  def prepareGlobalVariables(metaData: MetaData): Map[String, ObjectWithType] = {
    val globalVariablesWithType = globalVariablesWithMethodDef.mapValuesNow(toGlobalVariable(_, metaData))
    if (hideMetaVariable) {
      globalVariablesWithType
    } else {
      globalVariablesWithType + (VariableConstants.MetaVariableName -> MetaVariables.withType(metaData))
    }
  }

  def emptyValidationContext(metaData: MetaData): ValidationContext = validationContextWithLocalVariables(metaData, Map.empty)

  def validationContextWithLocalVariables(metaData: MetaData, localVariables: Map[String, TypingResult]): ValidationContext = ValidationContext(
    localVariables,
    prepareGlobalVariables(metaData).mapValuesNow(_.typ)
  )

  private def toGlobalVariable(objectWithMethodDef: ObjectWithMethodDef, metaData: MetaData): ObjectWithType = {
    objectWithMethodDef.obj match {
      case typedGlobalVariable: TypedGlobalVariable => ObjectWithType(typedGlobalVariable.value(metaData), typedGlobalVariable.returnType(metaData))
      case _ => ObjectWithType(objectWithMethodDef.obj, objectWithMethodDef.returnType)
    }
  }
}

object GlobalVariablesPreparer {

  def apply(expressionDefinition: ExpressionDefinition[ObjectWithMethodDef]): GlobalVariablesPreparer = {
    new GlobalVariablesPreparer(expressionDefinition.globalVariables, expressionDefinition.hideMetaVariable)
  }

}

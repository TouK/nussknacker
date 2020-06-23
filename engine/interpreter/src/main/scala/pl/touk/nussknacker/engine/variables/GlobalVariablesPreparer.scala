package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, ObjectWithType}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.util.Implicits._

class GlobalVariablesPreparer(userDefinedGlobalVariables: Map[String, ObjectWithType], hideMetaVariable: Boolean) {

  def prepareGlobalVariables(metaData: MetaData): Map[String, ObjectWithType] = {
    if (hideMetaVariable) {
      userDefinedGlobalVariables
    } else {
      userDefinedGlobalVariables + (Interpreter.MetaParamName -> MetaVariables.withType(metaData))
    }
  }

  def emptyValidationContext(metaData: MetaData): ValidationContext = ValidationContext(
    Map.empty,
    prepareGlobalVariables(metaData).mapValuesNow(_.typ)
  )

}

object GlobalVariablesPreparer {

  import pl.touk.nussknacker.engine.util.Implicits._

  def apply(expressionDefinition: ExpressionDefinition[ObjectWithMethodDef]): GlobalVariablesPreparer = {
    val userDefinedGlobalVariablesWithType = expressionDefinition.globalVariables.mapValuesNow(obj => ObjectWithType(obj.obj, obj.returnType))
    new GlobalVariablesPreparer(userDefinedGlobalVariablesWithType, expressionDefinition.hideMetaVariable)
  }

}
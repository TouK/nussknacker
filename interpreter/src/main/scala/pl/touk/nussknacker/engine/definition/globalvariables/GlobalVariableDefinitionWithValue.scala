package pl.touk.nussknacker.engine.definition.globalvariables

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.component.ObjectOperatingOnTypes

final class GlobalVariableDefinitionWithValue(variable: Any) extends ObjectOperatingOnTypes {

  override def definedTypes: List[TypingResult] = List(typ)

  def valueWithType(metaData: MetaData): ValueWithType = {
    variable match {
      case typedGlobalVariable: TypedGlobalVariable =>
        ValueWithType(typedGlobalVariable.value(metaData), typedGlobalVariable.returnType(metaData))
      case _ =>
        ValueWithType(variable, typeBasedOnValue)
    }
  }

  def typ: TypingResult = {
    variable match {
      case typedGlobalVariable: TypedGlobalVariable =>
        typedGlobalVariable.initialReturnType
      case _ =>
        typeBasedOnValue
    }
  }

  private val typeBasedOnValue = Typed.fromInstance(variable)

}

object GlobalVariableDefinitionWithValue {

  def apply(variable: Any) = new GlobalVariableDefinitionWithValue(variable)

}

case class ValueWithType(value: Any, typ: TypingResult)

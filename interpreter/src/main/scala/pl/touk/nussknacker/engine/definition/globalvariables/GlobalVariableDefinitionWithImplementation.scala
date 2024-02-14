package pl.touk.nussknacker.engine.definition.globalvariables

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.component.ObjectOperatingOnTypes

final class GlobalVariableDefinitionWithImplementation(variable: Any) extends ObjectOperatingOnTypes {

  override def definedTypes: List[TypingResult] = List(typ)

  def objectWithType(metaData: MetaData): ObjectWithType = {
    variable match {
      case typedGlobalVariable: TypedGlobalVariable =>
        ObjectWithType(typedGlobalVariable.value(metaData), typedGlobalVariable.returnType(metaData))
      case _ =>
        ObjectWithType(variable, typeBasedOnImplementation)
    }
  }

  def typ: TypingResult = {
    variable match {
      case typedGlobalVariable: TypedGlobalVariable =>
        typedGlobalVariable.initialReturnType
      case _ =>
        typeBasedOnImplementation
    }
  }

  private val typeBasedOnImplementation = Typed.fromInstance(variable)

}

object GlobalVariableDefinitionWithImplementation {

  def apply(variable: Any) = new GlobalVariableDefinitionWithImplementation(variable)

}

case class ObjectWithType(obj: Any, typ: TypingResult)

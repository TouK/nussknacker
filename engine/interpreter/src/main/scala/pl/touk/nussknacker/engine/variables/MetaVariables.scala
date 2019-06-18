package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, TypedMap, TypedObjectDefinition}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, MetaData}

final case class MetaVariables(processName: String, properties: TypedMap)

object MetaVariables {
  def withVariable(ctx: Context)(implicit metaData: MetaData): Context = {
    ctx.withVariable(Interpreter.MetaParamName, MetaVariables(metaData.id, properties(metaData)))
  }

  def typingResult(metaData: MetaData): TypingResult = TypedObjectTypingResult(Map(
    "processName" -> Typed[String],
    "properties" -> propertiesType(metaData)
  ))

  def withType(types: Map[String, TypingResult])(implicit metaData: MetaData): Map[String, TypingResult] = {
    types + (Interpreter.MetaParamName -> typingResult(metaData))
  }

  private def properties(meta: MetaData): TypedMap = {
    TypedMap(meta.additionalFields.map(_.properties).getOrElse(Map.empty))
  }

  private def propertiesType(metaData: MetaData) = {
    val definedProperties = metaData.additionalFields.map(_.properties).getOrElse(Map.empty)
    TypedObjectTypingResult(TypedObjectDefinition(definedProperties.mapValues(_ => ClazzRef[String])))
  }
}
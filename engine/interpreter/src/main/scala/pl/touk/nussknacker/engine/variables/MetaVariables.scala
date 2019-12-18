package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, TypedMap, TypedObjectDefinition}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithType

final case class MetaVariables(processName: String, properties: TypedMap)

object MetaVariables {

  def withType(metaData: MetaData): ObjectWithType =
    ObjectWithType(MetaVariables(metaData), typingResult(metaData))

  def apply(metaData: MetaData): MetaVariables =
    MetaVariables(metaData.id, properties(metaData))

  def typingResult(metaData: MetaData): TypingResult = TypedObjectTypingResult(Map(
    "processName" -> Typed[String],
    "properties" -> propertiesType(metaData)
  ))

  private def properties(meta: MetaData): TypedMap = {
    TypedMap(meta.additionalFields.map(_.properties).getOrElse(Map.empty))
  }

  private def propertiesType(metaData: MetaData) = {
    val definedProperties = metaData.additionalFields.map(_.properties).getOrElse(Map.empty)
    TypedObjectTypingResult(TypedObjectDefinition(definedProperties.mapValues(_ => ClazzRef[String])))
  }
}
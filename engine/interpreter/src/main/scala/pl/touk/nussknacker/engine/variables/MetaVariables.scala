package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.{Hidden, MetaData}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, TypedObjectDefinition}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithType

import scala.collection.immutable.ListMap

final case class MetaVariables(processName: String, properties: TypedMap)

object MetaVariables {

  @Hidden
  def withType(metaData: MetaData): ObjectWithType =
    ObjectWithType(MetaVariables(metaData), typingResult(metaData))

  @Hidden
  def apply(metaData: MetaData): MetaVariables =
    MetaVariables(metaData.id, properties(metaData))

  @Hidden
  def typingResult(metaData: MetaData): TypingResult = TypedObjectTypingResult(ListMap(
    "processName" -> Typed[String],
    "properties" -> propertiesType(metaData)
  ))

  private def properties(meta: MetaData): TypedMap = {
    TypedMap(meta.additionalFields.map(_.properties).getOrElse(Map.empty))
  }

  private def propertiesType(metaData: MetaData): TypedObjectTypingResult = {
    val definedProperties = metaData
      .additionalFields
      .map(_.properties)
      .map(_.toList.sortBy(_._1))
      .getOrElse(List.empty)

    val propertiesTyping = TypedObjectDefinition(
      definedProperties.map { case (propertyName, _) =>  propertyName -> Typed[String] }
    )
    TypedObjectTypingResult(propertiesTyping)
  }
}
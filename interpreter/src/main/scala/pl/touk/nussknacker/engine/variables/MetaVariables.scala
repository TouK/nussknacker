package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.{Hidden, MetaData}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, TypedObjectDefinition}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithType

final case class MetaVariables(processName: String, properties: TypedMap)

object MetaVariables {

  @Hidden
  def withType(metaData: MetaData): ObjectWithType =
    ObjectWithType(MetaVariables(metaData), typingResult(metaData))

  @Hidden
  def apply(metaData: MetaData): MetaVariables =
    MetaVariables(metaData.id, properties(metaData))

  @Hidden
  def typingResult(metaData: MetaData): TypingResult = TypedObjectTypingResult(
    Map(
      "processName" -> Typed[String],
      "properties"  -> propertiesType(metaData)
    )
  )

  private def properties(meta: MetaData): TypedMap = {
    TypedMap(meta.additionalFields.properties)
  }

  private def propertiesType(metaData: MetaData): TypedObjectTypingResult = {
    val definedProperties = metaData.additionalFields.properties.toList.sortBy(_._1)

    val propertiesTyping = TypedObjectDefinition(
      definedProperties.map { case (propertyName, _) => propertyName -> Typed[String] }.toMap
    )
    TypedObjectTypingResult(propertiesTyping)
  }

}

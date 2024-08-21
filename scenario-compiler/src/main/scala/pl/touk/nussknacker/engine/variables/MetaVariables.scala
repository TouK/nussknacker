package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{Hidden, MetaData}
import pl.touk.nussknacker.engine.definition.globalvariables.ObjectWithType

final case class MetaVariables(processName: String, processLabels: List[String], properties: TypedMap)

object MetaVariables {

  @Hidden
  def withType(metaData: MetaData): ObjectWithType =
    ObjectWithType(MetaVariables(metaData), typingResult(metaData))

  @Hidden
  def apply(metaData: MetaData): MetaVariables =
    MetaVariables(metaData.name.value, metaData.labels, properties(metaData))

  @Hidden
  def typingResult(metaData: MetaData): TypingResult =
    typingResult(metaData.additionalFields.properties.keys)

  @Hidden
  def typingResult(scenarioPropertiesNames: Iterable[String]): TypingResult = Typed.record(
    Map(
      "processName"   -> Typed[String],
      "processLabels" -> Typed.genericTypeClass[java.util.List[_]](List(Typed[String])),
      "properties"    -> propertiesType(scenarioPropertiesNames)
    )
  )

  private def properties(meta: MetaData): TypedMap = {
    TypedMap(meta.additionalFields.properties)
  }

  private def propertiesType(scenarioPropertiesNames: Iterable[String]): TypedObjectTypingResult = {
    Typed.record(scenarioPropertiesNames.map(_ -> Typed[String]))
  }

}

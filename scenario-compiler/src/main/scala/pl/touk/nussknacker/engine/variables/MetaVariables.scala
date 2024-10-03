package pl.touk.nussknacker.engine.variables

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{Hidden, JobData, MetaData}
import pl.touk.nussknacker.engine.definition.globalvariables.ObjectWithType
import scala.jdk.CollectionConverters._

final case class MetaVariables(processName: String, scenarioLabels: java.util.List[String], properties: TypedMap)

object MetaVariables {

  @Hidden
  def withType(jobData: JobData): ObjectWithType =
    ObjectWithType(MetaVariables(jobData), typingResult(jobData.metaData))

  @Hidden
  def apply(jobData: JobData): MetaVariables =
    MetaVariables(jobData.metaData.name.value, jobData.processVersion.labels.asJava, properties(jobData.metaData))

  @Hidden
  def typingResult(metaData: MetaData): TypingResult =
    typingResult(metaData.additionalFields.properties.keys)

  @Hidden
  def typingResult(scenarioPropertiesNames: Iterable[String]): TypingResult = Typed.record(
    Map(
      "processName"    -> Typed[String],
      "properties"     -> propertiesType(scenarioPropertiesNames),
      "scenarioLabels" -> Typed.genericTypeClass[java.util.List[_]](List(Typed[String])),
    )
  )

  private def properties(meta: MetaData): TypedMap = {
    TypedMap(meta.additionalFields.properties)
  }

  private def propertiesType(scenarioPropertiesNames: Iterable[String]): TypedObjectTypingResult = {
    Typed.record(scenarioPropertiesNames.map(_ -> Typed[String]))
  }

}

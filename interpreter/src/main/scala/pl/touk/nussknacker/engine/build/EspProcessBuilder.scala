package pl.touk.nussknacker.engine.build

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StandaloneMetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.GraphBuilder.Creator
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.{EspProcess, evaluatedparam}

class ProcessMetaDataBuilder private[build](metaData: MetaData) {

  //TODO: exception when non-streaming process?
  def parallelism(p: Int) =
    new ProcessMetaDataBuilder(metaData.copy(typeSpecificData = metaData.typeSpecificData.asInstanceOf[StreamMetaData].copy(parallelism = Some(p))))

  //TODO: exception when non-streaming process?
  def stateOnDisk(useStateOnDisk: Boolean) =
    new ProcessMetaDataBuilder(metaData.copy(typeSpecificData = metaData.typeSpecificData.asInstanceOf[StreamMetaData].copy(spillStateToDisk = Some(useStateOnDisk))))


  //TODO: exception when non-standalone process?
  def path(p: Option[String]) =
    new ProcessMetaDataBuilder(metaData.copy(typeSpecificData = StandaloneMetaData(p)))

  def subprocessVersions(subprocessVersions: Map[String, Long]) =
    new ProcessMetaDataBuilder(metaData.copy(subprocessVersions = subprocessVersions))

  def additionalFields(description: Option[String] = None,
                       properties: Map[String, String] = Map.empty) =
    new ProcessMetaDataBuilder(metaData.copy(
      additionalFields = Some(ProcessAdditionalFields(description, properties)))
    )

  def source(id: String, typ: String, params: (String, Expression)*): ProcessGraphBuilder =
    new ProcessGraphBuilder(GraphBuilder.source(id, typ, params: _*).creator
      .andThen(r => EspProcess(metaData, NonEmptyList.of(r))))

  class ProcessGraphBuilder private[ProcessMetaDataBuilder](val creator: Creator[EspProcess])
    extends GraphBuilder[EspProcess] {

    override def build(inner: Creator[EspProcess]) = new ProcessGraphBuilder(inner)
  }
}

object EspProcessBuilder {

  def id(id: String) =
    new ProcessMetaDataBuilder(MetaData(id, StreamMetaData()))

}

object StandaloneProcessBuilder {

  def id(id: ProcessName) =
    new ProcessMetaDataBuilder(MetaData(id.value, StandaloneMetaData(None)))

}

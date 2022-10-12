package pl.touk.nussknacker.engine.build

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.GraphBuilder.Creator
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SourceNode

class ProcessMetaDataBuilder private[build](metaData: MetaData) {

  def parallelism(p: Int): ProcessMetaDataBuilder = {
    val newTypeSpecificData = metaData.typeSpecificData match {
      case s: StreamMetaData => s.copy(parallelism = Some(p))
      case l: LiteStreamMetaData => l.copy(parallelism = Some(p))
      case other => throw new IllegalArgumentException(s"Given execution engine: ${other.getClass.getSimpleName} doesn't support parallelism parameter")
    }
    new ProcessMetaDataBuilder(metaData.copy(typeSpecificData = newTypeSpecificData))
  }

  //TODO: exception when non-streaming process?
  def stateOnDisk(useStateOnDisk: Boolean) =
    new ProcessMetaDataBuilder(metaData.copy(typeSpecificData = metaData.typeSpecificData.asInstanceOf[StreamMetaData].copy(spillStateToDisk = Some(useStateOnDisk))))

  //TODO: exception when non-streaming process?
  def useAsyncInterpretation(useAsyncInterpretation: Boolean) =
    new ProcessMetaDataBuilder(metaData.copy(typeSpecificData = metaData.typeSpecificData.asInstanceOf[StreamMetaData].copy(useAsyncInterpretation = Some(useAsyncInterpretation))))


  //TODO: exception when non-request-response process?
  def slug(p: Option[String]) =
    new ProcessMetaDataBuilder(metaData.copy(typeSpecificData = RequestResponseMetaData(p)))

  def subprocessVersions(subprocessVersions: Map[String, Long]) =
    new ProcessMetaDataBuilder(metaData.copy(subprocessVersions = subprocessVersions))

  def additionalFields(description: Option[String] = None,
                       properties: Map[String, String] = Map.empty) =
    new ProcessMetaDataBuilder(metaData.copy(
      additionalFields = Some(ProcessAdditionalFields(description, properties)))
    )

  def source(id: String, typ: String, params: (String, Expression)*): ProcessGraphBuilder =
    new ProcessGraphBuilder(
      GraphBuilder.source(id, typ, params: _*)
        .creator
        .andThen(r => EspProcess(metaData, NonEmptyList.of(r)).toCanonicalProcess)
    )

  def sources(source: SourceNode, rest: SourceNode*): CanonicalProcess = EspProcess(metaData, NonEmptyList.of(source, rest: _*)).toCanonicalProcess

  class ProcessGraphBuilder private[ProcessMetaDataBuilder](val creator: Creator[CanonicalProcess])
    extends GraphBuilder[CanonicalProcess] {

    override def build(inner: Creator[CanonicalProcess]) = new ProcessGraphBuilder(inner)
  }
}

object ScenarioBuilder {

  def streaming(id: String) =
    new ProcessMetaDataBuilder(MetaData(id, StreamMetaData()))

  def streamingLite(id: String) =
    new ProcessMetaDataBuilder(MetaData(id, LiteStreamMetaData()))

  def requestResponse(id: String) =
    new ProcessMetaDataBuilder(MetaData(id, RequestResponseMetaData(None)))

  def requestResponse(id: String, slug: String) =
    new ProcessMetaDataBuilder(MetaData(id, RequestResponseMetaData(Some(slug))))

}

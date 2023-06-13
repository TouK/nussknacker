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

  // TODO: exception when non-streaming process?
  def parallelism(p: Int): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(metaData.copy(
      additionalFields = additionalFields.copy(
        properties = additionalFields.properties ++ Map("parallelism" -> p.toString)
      )
    ))
  }

  //TODO: exception when non-streaming process?
  def stateOnDisk(useStateOnDisk: Boolean): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(metaData.copy(
      additionalFields = additionalFields.copy(
        properties = additionalFields.properties ++ Map("spillStateToDisk" -> useStateOnDisk.toString)
      )
    ))
  }

  //TODO: exception when non-streaming process?
  def useAsyncInterpretation(useAsyncInterpretation: Boolean): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(metaData.copy(
      additionalFields = additionalFields.copy(
        properties = additionalFields.properties ++ Map("useAsyncInterpretation" -> useAsyncInterpretation.toString)
      )
    ))
  }


  //TODO: exception when non-request-response process?
  def slug(slug: Option[String]): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(metaData.copy(
      additionalFields = additionalFields.copy(
        properties = additionalFields.properties ++ Map("slug" -> slug.getOrElse(""))
      )
    ))
  }

  def additionalFields(description: Option[String] = None,
                       properties: Map[String, String] = Map.empty): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(metaData.copy(
      additionalFields = additionalFields.copy(
        description = description,
        properties = additionalFields.properties ++ properties
      )
    ))
  }

  def source(id: String, typ: String, params: (String, Expression)*): ProcessGraphBuilder =
    new ProcessGraphBuilder(
      GraphBuilder.source(id, typ, params: _*)
        .creator
        .andThen(r => EspProcess(metaData, NonEmptyList.of(r)).toCanonicalProcess)
    )

  def sources(source: SourceNode, rest: SourceNode*): CanonicalProcess = EspProcess(metaData, NonEmptyList.of(source, rest: _*)).toCanonicalProcess

}

class ProcessGraphBuilder private[build](val creator: Creator[CanonicalProcess])
  extends GraphBuilder[CanonicalProcess] {

  override def build(inner: Creator[CanonicalProcess]) = new ProcessGraphBuilder(inner)
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

  def fragment(id: String, params: (String, Class[_])*): ProcessGraphBuilder = {
    new ProcessGraphBuilder(GraphBuilder.fragmentInput(id, params:_*)
      .creator
      .andThen(r => EspProcess(MetaData(id, FragmentSpecificData()), NonEmptyList.of(r)).toCanonicalProcess))
  }
}

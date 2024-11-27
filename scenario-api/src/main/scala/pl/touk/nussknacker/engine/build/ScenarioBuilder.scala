package pl.touk.nussknacker.engine.build

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.GraphBuilder.Creator
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter
import pl.touk.nussknacker.engine.graph.node.{SourceNode, SubsequentNode}

class ProcessMetaDataBuilder private[build] (metaData: MetaData) {

  // TODO: exception when non-streaming process?
  def parallelism(p: Int): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(
      metaData.copy(
        additionalFields = additionalFields.copy(
          properties = additionalFields.properties ++ Map("parallelism" -> p.toString)
        )
      )
    )
  }

  // TODO: exception when non-streaming process?
  def stateOnDisk(useStateOnDisk: Boolean): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(
      metaData.copy(
        additionalFields = additionalFields.copy(
          properties =
            additionalFields.properties ++ Map(StreamMetaData.spillStateToDiskName -> useStateOnDisk.toString)
        )
      )
    )
  }

  // TODO: exception when non-streaming process?
  def useAsyncInterpretation(useAsyncInterpretation: Boolean): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(
      metaData.copy(
        additionalFields = additionalFields.copy(
          properties = additionalFields.properties ++ Map(
            StreamMetaData.useAsyncInterpretationName -> useAsyncInterpretation.toString
          )
        )
      )
    )
  }

  // TODO: exception when non-request-response process?
  def slug(slug: Option[String]): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(
      metaData.copy(
        additionalFields = additionalFields.copy(
          properties = additionalFields.properties ++ Map(RequestResponseMetaData.slugName -> slug.getOrElse(""))
        )
      )
    )
  }

  def additionalFields(
      description: Option[String] = None,
      properties: Map[String, String] = Map.empty,
      showDescription: Boolean = false
  ): ProcessMetaDataBuilder = {
    val additionalFields = metaData.additionalFields
    new ProcessMetaDataBuilder(
      metaData.copy(
        additionalFields = additionalFields.copy(
          description = description,
          properties = additionalFields.properties ++ properties,
          showDescription = showDescription
        )
      )
    )
  }

  def source(id: String, typ: String, params: (String, Expression)*): ProcessGraphBuilder =
    new ProcessGraphBuilder(
      GraphBuilder
        .source(id, typ, params: _*)
        .creator
        .andThen(r => EspProcess(metaData, NonEmptyList.of(r)).toCanonicalProcess)
    )

  def sources(source: SourceNode, rest: SourceNode*): CanonicalProcess =
    EspProcess(metaData, NonEmptyList.of(source, rest: _*)).toCanonicalProcess

}

class ProcessGraphBuilder private[build] (val creator: Creator[CanonicalProcess])
    extends GraphBuilder[CanonicalProcess] {

  override def build(inner: Creator[CanonicalProcess]) = new ProcessGraphBuilder(inner)
}

object ScenarioBuilder {

  def streaming(id: String) =
    new ProcessMetaDataBuilder(MetaData(id, StreamMetaData()))

  def streamingLite(id: String) =
    new ProcessMetaDataBuilder(MetaData(id, LiteStreamMetaData()))

  /*
      FIXME: We should relax validation of scenario parameters inside UIProcessValidator.
      Thanks to that we could test scenarios created using this DSL without mocking.
   */
  def withCustomMetaData(id: String, properties: Map[String, String]) =
    new ProcessMetaDataBuilder(MetaData(id, CustomMetaData(properties)))

  def requestResponse(id: String) =
    new ProcessMetaDataBuilder(MetaData(id, RequestResponseMetaData(Some(id))))

  def requestResponse(id: String, slug: String) =
    new ProcessMetaDataBuilder(MetaData(id, RequestResponseMetaData(Some(slug))))

  def fragmentWithInputNodeId(id: String, inputNodeId: String, params: (String, Class[_])*): ProcessGraphBuilder = {
    createFragment(id, GraphBuilder.fragmentInput(inputNodeId, params: _*))
  }

  def fragmentWithRawParameters(id: String, params: FragmentParameter*): ProcessGraphBuilder = {
    createFragment(id, GraphBuilder.fragmentInputWithRawParameters(id, params: _*))
  }

  def fragment(id: String, params: (String, Class[_])*): ProcessGraphBuilder = {
    fragmentWithInputNodeId(id, id, params: _*)
  }

  private def createFragment(id: String, graphBuilder: GraphBuilder[SourceNode]): ProcessGraphBuilder = {
    new ProcessGraphBuilder(
      graphBuilder.creator
        .andThen(r => EspProcess(MetaData(id, FragmentSpecificData()), NonEmptyList.of(r)).toCanonicalProcess)
    )
  }

}

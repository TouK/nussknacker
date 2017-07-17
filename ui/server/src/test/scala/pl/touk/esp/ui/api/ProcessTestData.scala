package pl.touk.esp.ui.api

import pl.touk.esp.engine.api.StreamMetaData
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ObjectProcessDefinition, ProcessDefinition}
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node
import pl.touk.esp.engine.graph.node.Case
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.spel
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{Edge, NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.esp.ui.validation.ValidationResults.ValidationResult

object ProcessTestData {

  val existingSourceFactory = "barSource"
  val existingSinkFactory = "barSink"
  val otherExistingSinkFactory = "barSink"
  val existingServiceId = "barService"
  val existingStreamTransformer = "transformer"
  val otherExistingStreamTransformer = "otherTransformer"

  val processDefinition = ObjectProcessDefinition.empty
        .withSourceFactory(existingSourceFactory)
        .withSinkFactory(otherExistingSinkFactory)
        .withSinkFactory(existingSinkFactory)
        .withService(existingServiceId)
        .withCustomStreamTransformer(existingStreamTransformer, classOf[String],  CustomTransformerAdditionalData(Set("query1", "query2"), clearsContext = false))
        .withCustomStreamTransformer(otherExistingStreamTransformer, classOf[String], CustomTransformerAdditionalData(Set("query3"), clearsContext = false))

  val validator = ProcessValidator.default(processDefinition)

  val validProcess =
    EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", existingStreamTransformer)
      .sink("sink", existingSinkFactory)

  import spel.Implicits._

  val technicalValidProcess =
    EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", existingSourceFactory)
      .buildSimpleVariable("var1", "var1", "'foo'")
      .filter("filter1", "#var1 == 'foo'")
      .enricher("enricher1", "output1", existingServiceId)
      .switch("switch1", "1 == 1", "switchVal",
        Case("true", GraphBuilder
        .filter("filter2", "1 != 0")
        .enricher("enricher2", "output2", existingServiceId)
        .sink("sink1", existingSinkFactory))
        ,
        Case("false", GraphBuilder
          .filter("filter3", "1 != 0")
          .enricher("enricher3", "output3", existingServiceId)
          .sink("sink2", existingSinkFactory)
      ))

  val invalidProcess = {
    val missingSourceFactory = "fooSource"
    val missingSinkFactory = "fooSink"

    EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", missingSourceFactory)
      .sink("sink", missingSinkFactory)
  }

  val sampleDisplayableProcess = {
    DisplayableProcess(
      id = "fooProcess",
      properties = ProcessProperties(StreamMetaData(Some(2)), ExceptionHandlerRef(List.empty), false, Some(ProcessAdditionalFields(Some("process description")))),
      nodes = List(
        node.Source(
          id = "sourceId",
          ref = SourceRef(existingSourceFactory, List.empty),
          additionalFields = Some(NodeAdditionalFields(Some("node description")))
        ),
        node.Sink(
          id = "sinkId",
          ref = SinkRef(existingSinkFactory, List.empty),
          endResult = None,
          additionalFields = None
        )
      ),
      edges = List(Edge(from = "sourceId", to = "sinkId", edgeType = None)),
      processingType = ProcessingType.Streaming,
      validationResult = Some(ValidationResult.success)
    )
  }
}

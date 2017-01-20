package pl.touk.esp.ui.api

import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.{ObjectProcessDefinition, ProcessDefinition}
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.node
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{Edge, NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}

object ProcessTestData {

  val existingSourceFactory = "barSource"
  val existingSinkFactory = "barSink"
  val existingStreamTransformer = "transformer"

  val validator = ProcessValidator.default(
    ObjectProcessDefinition.empty
      .withSourceFactory(existingSourceFactory)
      .withSinkFactory(existingSinkFactory)
      .withCustomStreamTransformer(existingStreamTransformer, classOf[String])
  )

  val validProcess =
    EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", existingStreamTransformer)
      .sink("sink", existingSinkFactory)

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
      properties = ProcessProperties(Some(2), None, ExceptionHandlerRef(List.empty), Some(ProcessAdditionalFields(Some("process description")))),
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
      edges = List(Edge(from = "sourceId", to = "sinkId", label = None)),
      validationResult = ValidationResult.success
    )
  }
}

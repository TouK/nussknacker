package pl.touk.nussknacker.ui.api

import java.time.LocalDateTime

import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ObjectProcessDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.{EspProcess, node}
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.{ProcessType, ProcessingType}
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.{Edge, NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.nussknacker.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.nussknacker.ui.process.subprocess.{SetSubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.ui.validation.ValidationResults.ValidationResult

object ProcessTestData {

  val existingSourceFactory = "barSource"
  val existingSinkFactory = "barSink"
  val otherExistingSinkFactory = "barSink"
  val existingServiceId = "barService"
  val otherExistingServiceId = "fooService"

  val existingStreamTransformer = "transformer"
  val otherExistingStreamTransformer = "otherTransformer"

  val processDefinition = ObjectProcessDefinition.empty
        .withSourceFactory(existingSourceFactory)
        .withSinkFactory(otherExistingSinkFactory)
        .withSinkFactory(existingSinkFactory)
        .withService(existingServiceId)
        .withService(otherExistingServiceId)
        .withCustomStreamTransformer(existingStreamTransformer, classOf[String],  CustomTransformerAdditionalData(Set("query1", "query2"), clearsContext = false))
        .withCustomStreamTransformer(otherExistingStreamTransformer, classOf[String], CustomTransformerAdditionalData(Set("query3"), clearsContext = false))

  val validator = ProcessValidator.default(processDefinition)

  val validProcess : EspProcess =
    EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", existingSourceFactory)
      .processor("processor", existingServiceId)
      .customNode("custom", "out1", existingStreamTransformer)
      .sink("sink", existingSinkFactory)

  val validDisplayableProcess : DisplayableProcess = toValidatedDisplayable(validProcess)

  def toValidatedDisplayable(espProcess: EspProcess) : DisplayableProcess =
    ProcessConverter
     .toDisplayable(ProcessCanonizer.canonize(espProcess), ProcessingType.Streaming)
     .validated(new ProcessValidation(Map(ProcessingType.Streaming -> validator),
       new SubprocessResolver(new SetSubprocessRepository(Set()))))

  def toDetails(displayable: DisplayableProcess) : ProcessDetails =
    ProcessDetails(displayable.id, displayable.id, 1, true, None, ProcessType.Graph,
      ProcessingType.Streaming, "", LocalDateTime.now(), List(), Set(), Some(displayable), List(), None)



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

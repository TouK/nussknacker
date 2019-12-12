package pl.touk.nussknacker.ui.api.helpers

import java.time.LocalDateTime

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.CustomTransformerAdditionalData
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{Case, Split, SubprocessInputDefinition, SubprocessOutputDefinition, UserDefinedAdditionalNodeFields}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.{EspProcess, node}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder._
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessDetails, ValidatedProcessDetails}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.validation.ProcessValidation

object ProcessTestData {

  class SetSubprocessRepository(processes: Set[SubprocessDetails]) extends SubprocessRepository {
    override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] = {
      processes
    }
  }

  val existingSourceFactory = "barSource"
  val existingSinkFactory = "barSink"
  val otherExistingSinkFactory = "barSink"
  val existingServiceId = "barService"
  val otherExistingServiceId = "fooService"

  val existingStreamTransformer = "transformer"
  val otherExistingStreamTransformer = "otherTransformer"
  val otherExistingStreamTransformer2 = "otherTransformer2"

  val processDefinition = ProcessDefinitionBuilder.empty
        .withSourceFactory(existingSourceFactory)
        .withSinkFactory(otherExistingSinkFactory)
        .withSinkFactory(existingSinkFactory)
        .withService(existingServiceId)
        .withService(otherExistingServiceId)
        .withCustomStreamTransformer(existingStreamTransformer, classOf[String],  CustomTransformerAdditionalData(Set("query1", "query2"),
          clearsContext = false, manyInputs = false))
        .withCustomStreamTransformer(otherExistingStreamTransformer, classOf[String], CustomTransformerAdditionalData(Set("query3"),
          clearsContext = false, manyInputs = false))
        .withCustomStreamTransformer(otherExistingStreamTransformer2, classOf[String], CustomTransformerAdditionalData(Set("query4"),
          clearsContext = false, manyInputs = false))

  val validator = ProcessValidator.default(ProcessDefinitionBuilder.withEmptyObjects(processDefinition), new SimpleDictRegistry(Map.empty))

  val validation = new ProcessValidation(
    Map(TestProcessingTypes.Streaming -> validator),
    Map(TestProcessingTypes.Streaming -> Map()),
    new SubprocessResolver(new SetSubprocessRepository(Set())),
    Map.empty
  )

  val validProcess : EspProcess = validProcessWithId("fooProcess")

  def validProcessWithId(id: String) : EspProcess = EspProcessBuilder
        .id(id)
        .exceptionHandler()
        .source("source", existingSourceFactory)
        .processor("processor", existingServiceId)
        .customNode("custom", "out1", existingStreamTransformer)
        .emptySink("sink", existingSinkFactory)

  val validDisplayableProcess : ValidatedDisplayableProcess = toValidatedDisplayable(validProcess)
  val validProcessDetails: ValidatedProcessDetails = toDetails(validDisplayableProcess)

  def toValidatedDisplayable(espProcess: EspProcess) : ValidatedDisplayableProcess = {
    val displayable = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(espProcess), TestProcessingTypes.Streaming)
    new ValidatedDisplayableProcess(displayable, validation.validate(displayable))
  }

  def toDetails(displayable: DisplayableProcess) : ProcessDetails =
    BaseProcessDetails[DisplayableProcess](
      id = displayable.id,
      name = displayable.id,
      processVersionId = 1,
      isLatestVersion = true,
      description = None,
      isArchived = false,
      isSubprocess = false,
      processType = ProcessType.Graph,
      processingType = TestProcessingTypes.Streaming,
      processCategory = "Category",
      modificationDate = LocalDateTime.now(),
      tags = List(),
      currentlyDeployedAt = List(),
      currentDeployment = None,
      json = Some(displayable),
      history = List(),
      modelVersion = None
    )

  def toDetails(displayable: ValidatedDisplayableProcess) : ValidatedProcessDetails =
    BaseProcessDetails[ValidatedDisplayableProcess](
      id = displayable.id,
      name = displayable.id,
      processVersionId = 1,
      isLatestVersion = true,
      description = None,
      isArchived = false,
      isSubprocess = false,
      processType = ProcessType.Graph,
      processingType = TestProcessingTypes.Streaming,
      processCategory = "Category",
      modificationDate = LocalDateTime.now(),
      tags = List(),
      currentlyDeployedAt = List(),
      currentDeployment = None,
      json = Some(displayable),
      history = List(),
      modelVersion = None
    )



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
        .emptySink("sink1", existingSinkFactory))
        ,
        Case("false", GraphBuilder
          .filter("filter3", "1 != 0")
          .enricher("enricher3", "output3", existingServiceId)
          .emptySink("sink2", existingSinkFactory)
      ))

  val invalidProcess = {
    val missingSourceFactory = "fooSource"
    val missingSinkFactory = "fooSink"

    EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", missingSourceFactory)
      .emptySink("sink", missingSinkFactory)
  }

  val sampleDisplayableProcess = {
    DisplayableProcess(
      id = "fooProcess",
      properties = ProcessProperties(StreamMetaData(Some(2)), ExceptionHandlerRef(List.empty), false, Some(ProcessAdditionalFields(Some("process description"), Set.empty, Map.empty)), subprocessVersions = Map.empty),
      nodes = List(
        node.Source(
          id = "sourceId",
          ref = SourceRef(existingSourceFactory, List.empty),
          additionalFields = Some(UserDefinedAdditionalNodeFields(Some("node description")))
        ),
        node.Sink(
          id = "sinkId",
          ref = SinkRef(existingSinkFactory, List.empty),
          endResult = None,
          additionalFields = None
        )
      ),
      edges = List(Edge(from = "sourceId", to = "sinkId", edgeType = None)),
      processingType = TestProcessingTypes.Streaming
    )
  }

  val emptySubprocess = {
    CanonicalProcess(MetaData("sub1", StreamMetaData(), isSubprocess = true, None, Map()), ExceptionHandlerRef(List()), List(), None)
  }

  val sampleSubprocessOneOut = {
    CanonicalProcess(MetaData("sub1", StreamMetaData(), isSubprocess = true), ExceptionHandlerRef(List()), List(
      FlatNode(SubprocessInputDefinition("in", List(SubprocessParameter("param1", SubprocessClazzRef[String])))),
      canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output"))
    ), None)
  }

  val sampleSubprocess = {
    CanonicalProcess(MetaData("sub1", StreamMetaData(), isSubprocess = true), ExceptionHandlerRef(List()), List(
      FlatNode(SubprocessInputDefinition("in", List(SubprocessParameter("param1", SubprocessClazzRef[String])))),
      SplitNode(Split("split"), List(
        List(FlatNode(SubprocessOutputDefinition("out", "out1"))),
        List(FlatNode(SubprocessOutputDefinition("out2", "out2")))
      ))
    ), Some(List()))
  }

  val sampleSubprocess2 = {
    CanonicalProcess(MetaData("sub1", StreamMetaData(), isSubprocess = true), ExceptionHandlerRef(List()), List(
      FlatNode(SubprocessInputDefinition("in", List(SubprocessParameter("param2", SubprocessClazzRef[String])))),
      SplitNode(Split("split"), List(
        List(FlatNode(SubprocessOutputDefinition("out", "out1"))),
        List(FlatNode(SubprocessOutputDefinition("out2", "out2"))),
        List(FlatNode(SubprocessOutputDefinition("out3", "out2")))
      ))
    ), Some(List()))
  }

  def validProcessWithSubprocess(processName: ProcessName, subprocess: CanonicalProcess=sampleSubprocessOneOut): ProcessUsingSubprocess = {
    ProcessUsingSubprocess(
      process = EspProcessBuilder
        .id(processName.value)
        .exceptionHandler()
        .source("source", existingSourceFactory)
        .subprocess(subprocess.metaData.id, subprocess.metaData.id,Nil,Map(
          "output1" -> GraphBuilder.sink("sink", "'result1'", existingSinkFactory)
        )),
      subprocess = subprocess
    )
  }

  def displayableWithAdditionalFields(additionalFields: Option[ProcessAdditionalFields]): DisplayableProcess = {
    val process = validDisplayableProcess.toDisplayable
    val properties = process.properties

    process.copy(
      properties = properties.copy(
        additionalFields = additionalFields
      )
    )
  }

  case class ProcessUsingSubprocess(process: EspProcess, subprocess: CanonicalProcess)
}

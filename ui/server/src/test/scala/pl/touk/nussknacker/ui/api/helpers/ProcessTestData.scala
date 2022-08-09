package pl.touk.nussknacker.ui.api.helpers

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.CustomTransformerAdditionalData
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.{EspProcess, node}
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder._
import pl.touk.nussknacker.engine.{TypeSpecificInitialData, spel}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.processdetails.ValidatedProcessDetails
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{emptyProcessingTypeDataProvider, mapProcessingTypeDataProvider}
import pl.touk.nussknacker.ui.definition.editor.JavaSampleEnum
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import pl.touk.nussknacker.ui.validation.ProcessValidation

object ProcessTestData {

  import spel.Implicits._

  val existingSourceFactory = "barSource"
  val otherExistingSourceFactory = "fooSource"
  val secretExistingSourceFactory = "secretSource"

  val existingSinkFactory = "barSink"
  val existingSinkFactory2 = "barSink2"
  val otherExistingSinkFactory = "barSink"

  val existingServiceId = "barService"
  val otherExistingServiceId = "fooService"
  val otherExistingServiceId2 = "fooService2"
  val otherExistingServiceId3 = "fooService3"
  val notBlankExistingServiceId = "notBlank"
  val otherExistingServiceId4 = "fooService4"

  val processorId = "fooProcessor"

  val existingStreamTransformer = "transformer"
  val otherExistingStreamTransformer = "otherTransformer"
  val overriddenOtherExistingStreamTransformer = "overriddenOtherTransformer"
  val otherExistingStreamTransformer2 = "otherTransformer2"
  val optionalEndingStreamTransformer = "optionalEndingTransformer"

  val processDefinition: ProcessDefinitionExtractor.ProcessDefinition[DefinitionExtractor.ObjectDefinition] = ProcessDefinitionBuilder.empty
    .withSourceFactory(existingSourceFactory)
    .withSourceFactory(otherExistingSourceFactory)
    .withSourceFactory(secretExistingSourceFactory, TestCategories.SecretCategory)
    .withSinkFactory(otherExistingSinkFactory)
    .withSinkFactory(existingSinkFactory)
    .withService(existingServiceId)
    .withService(otherExistingServiceId)
    .withService(processorId, classOf[Void])
    .withService(otherExistingServiceId2, Parameter[Any]("expression"))
    .withService(otherExistingServiceId3, Parameter[String]("expression"))
    .withService(notBlankExistingServiceId, NotBlankParameter("expression", Typed.typedClass(classOf[String])))
    .withService(otherExistingServiceId4, Parameter[JavaSampleEnum]("expression").copy(
      editor = Some(FixedValuesParameterEditor(List(FixedExpressionValue("a", "a")))),
      validators = List(FixedValuesValidator(List(FixedExpressionValue("a", "a")))))
    )
    .withCustomStreamTransformer(existingStreamTransformer, classOf[String], CustomTransformerAdditionalData(Set("query1", "query2"),
      manyInputs = false, canBeEnding = false))
    .withCustomStreamTransformer(otherExistingStreamTransformer, classOf[String], CustomTransformerAdditionalData(Set("query3"),
      manyInputs = false, canBeEnding = false))
    .withCustomStreamTransformer(otherExistingStreamTransformer2, classOf[String], CustomTransformerAdditionalData(Set("query4"),
      manyInputs = false, canBeEnding = false))
    .withCustomStreamTransformer(optionalEndingStreamTransformer, classOf[String], CustomTransformerAdditionalData(Set("query5"),
      manyInputs = false, canBeEnding = true))

  val processValidation: ProcessValidation = ProcessValidation(
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> new StubModelDataWithProcessDefinition(processDefinition)),
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map()),
    new SubprocessResolver(new StubSubprocessRepository(Set())),
    emptyProcessingTypeDataProvider
  )

  val validProcess: EspProcess = validProcessWithId("fooProcess")

  val validProcessWithEmptyExpr: EspProcess = validProcessWithParam("fooProcess", "expression" -> Expression("spel", ""))

  val validDisplayableProcess: ValidatedDisplayableProcess = toValidatedDisplayable(validProcess)

  val validProcessDetails: ValidatedProcessDetails = TestProcessUtil.validatedToProcess(validDisplayableProcess)

  def validProcessWithId(id: String): EspProcess = ScenarioBuilder
    .streaming(id)
    .source("source", existingSourceFactory)
    .processor("processor", existingServiceId)
    .customNode("custom", "out1", existingStreamTransformer)
    .emptySink("sink", existingSinkFactory)

  def validProcessWithParam(id: String, param: (String, Expression)): EspProcess = ScenarioBuilder
    .streaming(id)
    .source("source", existingSourceFactory)
    .processor("processor", existingServiceId)
    .customNode("custom", "out1", otherExistingServiceId2, param)
    .emptySink("sink", existingSinkFactory)

  def toValidatedDisplayable(espProcess: EspProcess): ValidatedDisplayableProcess = {
    val displayable = ProcessConverter.toDisplayable(espProcess.toCanonicalProcess, TestProcessingTypes.Streaming)
    new ValidatedDisplayableProcess(displayable, processValidation.validate(displayable, TestCategories.Category1))
  }

  val multipleSourcesValidProcess: ValidatedDisplayableProcess = toValidatedDisplayable(EspProcess(MetaData("fooProcess", StreamMetaData()), NonEmptyList.of(
    GraphBuilder
      .source("source1", existingSourceFactory)
      .branchEnd("branch1", "join1"),
    GraphBuilder
      .source("source2", existingSourceFactory)
      .branchEnd("branch2", "join1"),
    GraphBuilder
      .join("join1", "union", Some("outPutVar"),
        List(
          "branch1" -> List("key" -> "'key1'", "value" -> "#input.data1"),
          "branch2" -> List("key" -> "'key2'", "value" -> "#input.data2")
        )
      )
      .filter("always-true-filter", """#outPutVar.key != "not key1 or key2"""")
      .emptySink("sink1", existingSinkFactory))
  ))

  val technicalValidProcess: EspProcess =
    ScenarioBuilder
      .streaming("fooProcess")
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

  val invalidProcess: EspProcess = {
    val missingSourceFactory = "missingSource"
    val missingSinkFactory = "fooSink"

    ScenarioBuilder
      .streaming("fooProcess")
      .source("source", missingSourceFactory)
      .emptySink("sink", missingSinkFactory)
  }

  val invalidProcessWithEmptyMandatoryParameter: EspProcess = {
    ScenarioBuilder.streaming("fooProcess")
      .source("source", existingSourceFactory)
      .enricher("custom", "out1", otherExistingServiceId3, "expression" -> "")
      .emptySink("sink", existingSinkFactory)
  }

  val invalidProcessWithBlankParameter: EspProcess =
    ScenarioBuilder.streaming("fooProcess")
      .source("source", existingSourceFactory)
      .enricher("custom", "out1", notBlankExistingServiceId, "expression" -> "''")
      .emptySink("sink", existingSinkFactory)

  val invalidProcessWithWrongFixedExpressionValue: EspProcess = {
    ScenarioBuilder.streaming("fooProcess")
      .source("source", existingSourceFactory)
      .enricher("custom", "out1", otherExistingServiceId4, "expression" -> "wrong fixed value")
      .emptySink("sink", existingSinkFactory)
  }

  val processWithInvalidAdditionalProperties: DisplayableProcess = DisplayableProcess(
    id = "fooProcess",
    properties = ProcessProperties(StreamMetaData(
      Some(2)),
      Some(ProcessAdditionalFields(Some("scenario description"), Map(
        "maxEvents" -> "text",
        "unknown" -> "x",
        "numberOfThreads" -> "wrong fixed value"
      ))),
      subprocessVersions = Map.empty),
    nodes = List.empty,
    edges = List.empty,
    processingType = TestProcessingTypes.Streaming
  )

  val sampleDisplayableProcess: DisplayableProcess = {
    DisplayableProcess(
      id = "fooProcess",
      properties = ProcessProperties(StreamMetaData(Some(2)), Some(ProcessAdditionalFields(Some("process description"), Map.empty)), subprocessVersions = Map.empty),
      nodes = List(
        node.Source(
          id = "sourceId",
          ref = SourceRef(existingSourceFactory, List.empty),
          additionalFields = Some(UserDefinedAdditionalNodeFields(Some("node description"), None))
        ),
        node.Sink(
          id = "sinkId",
          ref = SinkRef(existingSinkFactory, List.empty),
          additionalFields = None
        )
      ),
      edges = List(Edge(from = "sourceId", to = "sinkId", edgeType = None)),
      processingType = TestProcessingTypes.Streaming
    )
  }

  val emptySubprocess = {
    CanonicalProcess(MetaData("sub1", FragmentSpecificData(), None, Map()), List(), List.empty)
  }

  val sampleSubprocessOneOut = {
    CanonicalProcess(MetaData("sub1", FragmentSpecificData()), List(
      FlatNode(SubprocessInputDefinition("in", List(SubprocessParameter("param1", SubprocessClazzRef[String])))),
      canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))
    ), List.empty)
  }

  val sampleSubprocess = {
    CanonicalProcess(MetaData("sub1", FragmentSpecificData()), List(
      FlatNode(SubprocessInputDefinition("in", List(SubprocessParameter("param1", SubprocessClazzRef[String])))),
      SplitNode(Split("split"), List(
        List(FlatNode(SubprocessOutputDefinition("out", "out1", List.empty))),
        List(FlatNode(SubprocessOutputDefinition("out2", "out2", List.empty)))
      ))
    ), List.empty)
  }

  val sampleSubprocess2 = {
    CanonicalProcess(MetaData("sub1", FragmentSpecificData()), List(
      FlatNode(SubprocessInputDefinition("in", List(SubprocessParameter("param2", SubprocessClazzRef[String])))),
      SplitNode(Split("split"), List(
        List(FlatNode(SubprocessOutputDefinition("out", "out1", List.empty))),
        List(FlatNode(SubprocessOutputDefinition("out2", "out2", List.empty))),
        List(FlatNode(SubprocessOutputDefinition("out3", "out2", List.empty)))
      ))
    ), List.empty)
  }

  def createEmptyUpdateProcessCommand(processName: ProcessName, comment: Option[UpdateProcessComment]): UpdateProcessCommand = {
    val displayableProcess = DisplayableProcess(
      id = processName.value,
      properties = ProcessProperties(
        StreamMetaData(),
        None,
        subprocessVersions = Map.empty
      ),
      nodes = List.empty,
      edges = List.empty,
      processingType = TestProcessingTypes.Streaming
    )

    UpdateProcessCommand(displayableProcess, comment.getOrElse(UpdateProcessComment("")))
  }

  def validProcessWithSubprocess(processName: ProcessName, subprocess: CanonicalProcess = sampleSubprocessOneOut): ProcessUsingSubprocess = {
    ProcessUsingSubprocess(
      process = ScenarioBuilder
        .streaming(processName.value)
        .source("source", existingSourceFactory)
        .subprocess(subprocess.metaData.id, subprocess.metaData.id, Nil, Map(
          "output1" -> GraphBuilder.emptySink("sink", existingSinkFactory)
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

  val streamingTypeSpecificInitialData: TypeSpecificInitialData = TypeSpecificInitialData(StreamMetaData(None))

}

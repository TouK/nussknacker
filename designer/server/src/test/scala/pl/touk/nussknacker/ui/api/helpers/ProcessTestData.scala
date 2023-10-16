package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.CustomTransformerAdditionalData
import pl.touk.nussknacker.engine.definition.{ComponentIdProvider, DefinitionExtractor, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.kafka.KafkaFactory
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder._
import pl.touk.nussknacker.engine.MetaDataInitializer
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.processdetails.{ProcessDetails, ValidatedProcessDetails}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.definition.editor.JavaSampleEnum
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.validation.ProcessValidation

object ProcessTestData {

  import pl.touk.nussknacker.engine.spel.Implicits._
  import KafkaFactory._

  val existingSourceFactory       = "barSource"
  val otherExistingSourceFactory  = "fooSource"
  val secretExistingSourceFactory = "secretSource"
  val csvSourceFactory            = "csv-source"

  val existingSinkFactory            = "barSink"
  val existingSinkFactory2           = "barSink2"
  val existingSinkFactoryKafkaString = "kafka-string"
  val otherExistingSinkFactory       = "barSink"

  val existingServiceId         = "barService"
  val otherExistingServiceId    = "fooService"
  val otherExistingServiceId2   = "fooService2"
  val otherExistingServiceId3   = "fooService3"
  val notBlankExistingServiceId = "notBlank"
  val otherExistingServiceId4   = "fooService4"

  val processorId = "fooProcessor"

  val existingStreamTransformer                = "transformer"
  val otherExistingStreamTransformer           = "otherTransformer"
  val overriddenOtherExistingStreamTransformer = "overriddenOtherTransformer"
  val otherExistingStreamTransformer2          = "otherTransformer2"
  val optionalEndingStreamTransformer          = "optionalEndingTransformer"

  class SimpleTestComponentIdProvider extends ComponentIdProvider {
    def createComponentId(processingType: String, name: Option[String], componentType: ComponentType): ComponentId =
      ComponentId.default(processingType, name.getOrElse(""), componentType)

    def nodeToComponentId(processingType: String, node: NodeData): Option[ComponentId] = ???
  }

  private val processDefinition: ProcessDefinitionExtractor.ProcessDefinition[DefinitionExtractor.ObjectDefinition] =
    ProcessDefinitionBuilder.empty
      .withSourceFactory(existingSourceFactory)
      .withSourceFactory(otherExistingSourceFactory)
      .withSourceFactory(csvSourceFactory)
      .withSourceFactory(secretExistingSourceFactory, TestCategories.SecretCategory)
      .withSinkFactory(otherExistingSinkFactory)
      .withSinkFactory(existingSinkFactory)
      .withSinkFactory(
        existingSinkFactoryKafkaString,
        Parameter[String](TopicParamName),
        Parameter[Any](SinkValueParamName).copy(isLazyParameter = true)
      )
      .withService(existingServiceId)
      .withService(otherExistingServiceId)
      .withService(processorId, None)
      .withService(otherExistingServiceId2, Parameter[Any]("expression"))
      .withService(otherExistingServiceId3, Parameter[String]("expression"))
      .withService(notBlankExistingServiceId, NotBlankParameter("expression", Typed[String]))
      .withService(
        otherExistingServiceId4,
        Parameter[JavaSampleEnum]("expression").copy(
          editor = Some(FixedValuesParameterEditor(List(FixedExpressionValue("a", "a")))),
          validators = List(FixedValuesValidator(List(FixedExpressionValue("a", "a"))))
        )
      )
      .withCustomStreamTransformer(
        existingStreamTransformer,
        Some(Typed[String]),
        CustomTransformerAdditionalData(manyInputs = false, canBeEnding = false)
      )
      .withCustomStreamTransformer(
        otherExistingStreamTransformer,
        Some(Typed[String]),
        CustomTransformerAdditionalData(manyInputs = false, canBeEnding = false)
      )
      .withCustomStreamTransformer(
        otherExistingStreamTransformer2,
        Some(Typed[String]),
        CustomTransformerAdditionalData(manyInputs = false, canBeEnding = false)
      )
      .withCustomStreamTransformer(
        optionalEndingStreamTransformer,
        Some(Typed[String]),
        CustomTransformerAdditionalData(manyInputs = false, canBeEnding = true)
      )

  val processDefinitionWithIds
      : ProcessDefinitionExtractor.ProcessDefinitionWithComponentIds[DefinitionExtractor.ObjectDefinition] =
    processDefinition.withComponentIds(new SimpleTestComponentIdProvider, TestProcessingTypes.Streaming)

  def processValidation: ProcessValidation = ProcessValidation(
    mapProcessingTypeDataProvider(
      TestProcessingTypes.Streaming -> new StubModelDataWithProcessDefinition(processDefinition)
    ),
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map()),
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Nil),
    new FragmentResolver(new StubFragmentRepository(Set()))
  )

  val validProcess: CanonicalProcess = validProcessWithId("fooProcess")

  val validProcessWithEmptySpelExpr: CanonicalProcess =
    validProcessWithParam("fooProcess", "expression" -> Expression.spel(""))

  val validDisplayableProcess: ValidatedDisplayableProcess = toValidatedDisplayable(validProcess)

  val validProcessDetails: ValidatedProcessDetails = TestProcessUtil.validatedToProcess(validDisplayableProcess)

  val archivedValidProcessDetails: ValidatedProcessDetails =
    TestProcessUtil.validatedToProcess(validDisplayableProcess).copy(isArchived = true)

  def validProcessWithId(id: String): CanonicalProcess = ScenarioBuilder
    .streaming(id)
    .source("source", existingSourceFactory)
    .processor("processor", existingServiceId)
    .customNode("custom", "out1", existingStreamTransformer)
    .emptySink("sink", existingSinkFactory)

  def validProcessWithParam(id: String, param: (String, Expression)): CanonicalProcess = ScenarioBuilder
    .streaming(id)
    .source("source", existingSourceFactory)
    .processor("processor", existingServiceId)
    .customNode("custom", "out1", otherExistingServiceId2, param)
    .emptySink("sink", existingSinkFactory)

  def toValidatedDisplayable(
      espProcess: CanonicalProcess,
      category: String = TestCategories.TestCat
  ): ValidatedDisplayableProcess = {
    val displayable = ProcessConverter.toDisplayable(espProcess, TestProcessingTypes.Streaming, category)
    new ValidatedDisplayableProcess(displayable, processValidation.validate(displayable))
  }

  val multipleSourcesValidProcess: ValidatedDisplayableProcess = toValidatedDisplayable(
    ScenarioBuilder
      .streaming("fooProcess")
      .sources(
        GraphBuilder
          .source("source1", existingSourceFactory)
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("source2", existingSourceFactory)
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join(
            "join1",
            "union",
            Some("outputVar"),
            List(
              "branch1" -> List("key" -> "'key1'", "value" -> "#input.data1"),
              "branch2" -> List("key" -> "'key2'", "value" -> "#input.data2")
            )
          )
          .filter("always-true-filter", """#outputVar.key != "not key1 or key2"""")
          .emptySink("sink1", existingSinkFactory)
      )
  )

  val technicalValidProcess: CanonicalProcess =
    ScenarioBuilder
      .streaming("fooProcess")
      .source("source", existingSourceFactory)
      .buildSimpleVariable("var1", "var1", "'foo'")
      .filter("filter1", "#var1 == 'foo'")
      .enricher("enricher1", "output1", existingServiceId)
      .switch(
        "switch1",
        "1 == 1",
        "switchVal",
        Case(
          "true",
          GraphBuilder
            .filter("filter2", "1 != 0")
            .enricher("enricher2", "output2", existingServiceId)
            .emptySink("sink1", existingSinkFactory)
        ),
        Case(
          "false",
          GraphBuilder
            .filter("filter3", "1 != 0")
            .enricher("enricher3", "output3", existingServiceId)
            .emptySink("sink2", existingSinkFactory)
        )
      )

  val invalidProcess: CanonicalProcess = {
    val missingSourceFactory = "missingSource"
    val missingSinkFactory   = "fooSink"

    ScenarioBuilder
      .streaming("fooProcess")
      .source("source", missingSourceFactory)
      .emptySink("sink", missingSinkFactory)
  }

  val invalidProcessWithEmptyMandatoryParameter: CanonicalProcess = {
    ScenarioBuilder
      .streaming("fooProcess")
      .source("source", existingSourceFactory)
      .enricher("custom", "out1", otherExistingServiceId3, "expression" -> "")
      .emptySink("sink", existingSinkFactory)
  }

  val invalidProcessWithBlankParameter: CanonicalProcess =
    ScenarioBuilder
      .streaming("fooProcess")
      .source("source", existingSourceFactory)
      .enricher("custom", "out1", notBlankExistingServiceId, "expression" -> "''")
      .emptySink("sink", existingSinkFactory)

  val invalidProcessWithWrongFixedExpressionValue: CanonicalProcess = {
    ScenarioBuilder
      .streaming("fooProcess")
      .source("source", existingSourceFactory)
      .enricher("custom", "out1", otherExistingServiceId4, "expression" -> "wrong fixed value")
      .emptySink("sink", existingSinkFactory)
  }

  val processWithInvalidScenarioProperties: DisplayableProcess = DisplayableProcess(
    id = "fooProcess",
    properties = ProcessProperties.combineTypeSpecificProperties(
      StreamMetaData(Some(2)),
      ProcessAdditionalFields(
        Some("scenario description"),
        Map(
          "maxEvents"       -> "text",
          "unknown"         -> "x",
          "numberOfThreads" -> "wrong fixed value"
        ),
        StreamMetaData.typeName
      )
    ),
    nodes = List.empty,
    edges = List.empty,
    processingType = TestProcessingTypes.Streaming,
    TestCategories.TestCat
  )

  val sampleDisplayableProcess: DisplayableProcess = {
    DisplayableProcess(
      id = "fooProcess",
      properties = ProcessProperties.combineTypeSpecificProperties(
        StreamMetaData(Some(2)),
        ProcessAdditionalFields(Some("process description"), Map.empty, StreamMetaData.typeName)
      ),
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
      processingType = TestProcessingTypes.Streaming,
      TestCategories.TestCat
    )
  }

  val emptyFragment = {
    CanonicalProcess(MetaData("sub1", FragmentSpecificData()), List(), List.empty)
  }

  val sampleFragmentOneOut = {
    CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter("param1", FragmentClazzRef[String])))),
        canonicalnode.FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )
  }

  val sampleFragment = {
    CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter("param1", FragmentClazzRef[String])))),
        SplitNode(
          Split("split"),
          List(
            List(FlatNode(FragmentOutputDefinition("out", "out1", List.empty))),
            List(FlatNode(FragmentOutputDefinition("out2", "out2", List.empty)))
          )
        )
      ),
      List.empty
    )
  }

  val sampleFragment2 = {
    CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter("param2", FragmentClazzRef[String])))),
        SplitNode(
          Split("split"),
          List(
            List(FlatNode(FragmentOutputDefinition("out", "out1", List.empty))),
            List(FlatNode(FragmentOutputDefinition("out2", "out2", List.empty))),
            List(FlatNode(FragmentOutputDefinition("out3", "out2", List.empty)))
          )
        )
      ),
      List.empty
    )
  }

  def createEmptyUpdateProcessCommand(
      processName: ProcessName,
      comment: Option[UpdateProcessComment]
  ): UpdateProcessCommand = {
    val displayableProcess = DisplayableProcess(
      id = processName.value,
      properties = ProcessProperties(StreamMetaData(Some(1), Some(true))),
      nodes = List.empty,
      edges = List.empty,
      processingType = TestProcessingTypes.Streaming,
      TestCategories.Category1
    )

    UpdateProcessCommand(displayableProcess, comment.getOrElse(UpdateProcessComment("")), None)
  }

  def validProcessWithFragment(
      processName: ProcessName,
      fragment: CanonicalProcess = sampleFragmentOneOut
  ): ProcessUsingFragment = {
    ProcessUsingFragment(
      process = ScenarioBuilder
        .streaming(processName.value)
        .source("source", existingSourceFactory)
        .fragment(
          fragment.metaData.id,
          fragment.metaData.id,
          Nil,
          Map.empty,
          Map(
            "output1" -> GraphBuilder.emptySink("sink", existingSinkFactory)
          )
        ),
      fragment = fragment
    )
  }

  def displayableWithAdditionalFields(additionalFields: Option[ProcessAdditionalFields]): DisplayableProcess = {
    val process    = validDisplayableProcess.toDisplayable
    val properties = process.properties

    process.copy(
      properties = properties.copy(
        additionalFields =
          additionalFields.getOrElse(ProcessAdditionalFields(None, Map.empty, properties.additionalFields.metaDataType))
      )
    )
  }

  final case class ProcessUsingFragment(process: CanonicalProcess, fragment: CanonicalProcess)

  val streamingTypeSpecificInitialData: MetaDataInitializer = MetaDataInitializer(
    StreamMetaData.typeName,
    Map(StreamMetaData.parallelismName -> "1", StreamMetaData.spillStateToDiskName -> "true")
  )

}

package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.MetaDataInitializer
import pl.touk.nussknacker.engine.api.component.ComponentGroupName
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  CustomComponentSpecificData
}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.kafka.KafkaFactory
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil.toDisplayable
import pl.touk.nussknacker.ui.definition.editor.JavaSampleEnum
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.validation.UIProcessValidator

object ProcessTestData {

  import KafkaFactory._
  import pl.touk.nussknacker.engine.spel.Implicits._

  val existingSourceFactory      = "barSource"
  val otherExistingSourceFactory = "fooSource"
  val csvSourceFactory           = "csv-source"

  val existingSinkFactory            = "barSink"
  val existingSinkFactory2           = "barSink2"
  val existingSinkFactoryKafkaString = "kafka-string"

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
  val union                                    = "union"

  def modelDefinition(
      groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]] = Map.empty
  ): ModelDefinition[ComponentDefinitionWithImplementation] =
    ModelDefinitionBuilder
      .empty(groupNameMapping)
      .withSource(existingSourceFactory)
      .withSource(otherExistingSourceFactory)
      .withSource(csvSourceFactory)
      .withSink(existingSinkFactory)
      .withSink(
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
      .withCustom(
        existingStreamTransformer,
        Some(Typed[String]),
        CustomComponentSpecificData(manyInputs = false, canBeEnding = false),
      )
      .withCustom(
        otherExistingStreamTransformer,
        Some(Typed[String]),
        CustomComponentSpecificData(manyInputs = false, canBeEnding = false),
      )
      .withCustom(
        otherExistingStreamTransformer2,
        Some(Typed[String]),
        CustomComponentSpecificData(manyInputs = false, canBeEnding = false),
      )
      .withCustom(
        optionalEndingStreamTransformer,
        Some(Typed[String]),
        CustomComponentSpecificData(manyInputs = false, canBeEnding = true),
      )
      .withCustom(
        union,
        Some(Unknown),
        CustomComponentSpecificData(manyInputs = true, canBeEnding = true),
      )
      .build

  def processValidator: UIProcessValidator = new UIProcessValidator(
    ProcessValidator.default(new StubModelDataWithModelDefinition(modelDefinition())),
    Map.empty,
    List.empty,
    new FragmentResolver(new StubFragmentRepository(Map.empty))
  )

  val validProcess: CanonicalProcess = validProcessWithName(ProcessName("fooProcess"))

  val validProcessWithEmptySpelExpr: CanonicalProcess =
    validProcessWithParam("fooProcess", "expression" -> Expression.spel(""))

  val validDisplayableProcess: DisplayableProcess = toDisplayable(validProcess)

  val validProcessDetails: ScenarioWithDetails = TestProcessUtil.wrapWithDetails(validDisplayableProcess)

  val archivedValidProcessDetails: ScenarioWithDetails =
    TestProcessUtil.wrapWithDetails(validDisplayableProcess).copy(isArchived = true)

  def validProcessWithName(name: ProcessName): CanonicalProcess = ScenarioBuilder
    .streaming(name.value)
    .source("source", existingSourceFactory)
    .processor("processor", existingServiceId)
    .customNode("custom", "out1", existingStreamTransformer)
    .emptySink("sink", existingSinkFactory)

  def validProcessWithNodeId(nodeId: String): CanonicalProcess = ScenarioBuilder
    .streaming("fooProcess")
    .source(nodeId, existingSourceFactory)
    .emptySink("sink", existingSinkFactory)

  def validProcessWithParam(id: String, param: (String, Expression)): CanonicalProcess = ScenarioBuilder
    .streaming(id)
    .source("source", existingSourceFactory)
    .processor("processor", existingServiceId)
    .customNode("custom", "out1", otherExistingServiceId2, param)
    .emptySink("sink", existingSinkFactory)

  val multipleSourcesValidProcess: DisplayableProcess = toDisplayable(
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
              "branch1" -> List.empty,
              "branch2" -> List.empty
            )
          )
          .emptySink("sink1", existingSinkFactory)
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
    name = ProcessName("fooProcess"),
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
    TestCategories.Category1
  )

  val sampleDisplayableProcess: DisplayableProcess = {
    DisplayableProcess(
      name = ProcessName("fooProcess"),
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
      TestCategories.Category1
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
      name = processName,
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
          fragment.name.value,
          fragment.name.value,
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
    val process    = validDisplayableProcess
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

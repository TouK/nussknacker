package pl.touk.nussknacker.test.utils.domain

import pl.touk.nussknacker.engine.MetaDataInitializer
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ProcessingMode}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.component.CustomComponentSpecificData
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.spelTemplate
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.kafka.KafkaFactory
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioParameters, ScenarioWithDetailsForMigrations}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.{StubFragmentRepository, StubModelDataWithModelDefinition}
import pl.touk.nussknacker.ui.definition.editor.JavaSampleEnum
import pl.touk.nussknacker.ui.process.ProcessService.UpdateScenarioCommand
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
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

  val existingServiceId            = "barService"
  val otherExistingServiceId       = "fooService"
  val otherExistingServiceId2      = "fooService2"
  val otherExistingServiceId3      = "fooService3"
  val notBlankExistingServiceId    = "notBlank"
  val otherExistingServiceId4      = "fooService4"
  val dictParameterEditorServiceId = "dictParameterEditorService"

  val processorId = "fooProcessor"

  val existingStreamTransformer                = "transformer"
  val otherExistingStreamTransformer           = "otherTransformer"
  val overriddenOtherExistingStreamTransformer = "overriddenOtherTransformer"
  val otherExistingStreamTransformer2          = "otherTransformer2"
  val optionalEndingStreamTransformer          = "optionalEndingTransformer"
  val union                                    = "union"

  def modelDefinition(
      groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]] = Map.empty
  ): ModelDefinition =
    ModelDefinitionBuilder
      .empty(groupNameMapping)
      .withUnboundedStreamSource(existingSourceFactory)
      .withUnboundedStreamSource(otherExistingSourceFactory)
      .withUnboundedStreamSource(csvSourceFactory)
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
      .withService(
        dictParameterEditorServiceId,
        Parameter[String]("expression").copy(editor = Some(DictParameterEditor("someDictId")), validators = List.empty)
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

  def modelDefinitionWithDicts(
      dictionaries: Map[String, DictDefinition]
  ): ModelDefinition = {
    val definition = modelDefinition()

    definition.copy(
      expressionConfig = definition.expressionConfig.copy(dictionaries = dictionaries)
    )
  }

  def processValidator: UIProcessValidator = new UIProcessValidator(
    processingType = Streaming.stringify,
    validator = ProcessValidator.default(new StubModelDataWithModelDefinition(modelDefinition())),
    scenarioProperties = Map.empty,
    additionalValidators = List.empty,
    fragmentResolver = new FragmentResolver(new StubFragmentRepository(Map.empty))
  )

  def processValidatorWithDicts(dictionaries: Map[String, DictDefinition]): UIProcessValidator = new UIProcessValidator(
    processingType = Streaming.stringify,
    validator = ProcessValidator.default(new StubModelDataWithModelDefinition(modelDefinitionWithDicts(dictionaries))),
    scenarioProperties = Map.empty,
    additionalValidators = List.empty,
    fragmentResolver = new FragmentResolver(new StubFragmentRepository(Map.empty))
  )

  val sampleScenarioParameters: ScenarioParameters =
    ScenarioParameters(ProcessingMode.UnboundedStream, "Category1", EngineSetupName("Stub Engine"))

  val sampleProcessName: ProcessName = ProcessName("fooProcess")

  val validProcess: CanonicalProcess = validProcessWithName(sampleProcessName)

  val validProcessWithEmptySpelExpr: CanonicalProcess =
    validProcessWithParam("fooProcess", "expression" -> Expression.spel(""))

  val validScenarioGraph: ScenarioGraph = CanonicalProcessConverter.toScenarioGraph(validProcess)

  val validScenarioDetailsForMigrations: ScenarioWithDetailsForMigrations =
    TestProcessUtil.wrapWithDetailsForMigration(validScenarioGraph)

  val archivedValidScenarioDetailsForMigrations: ScenarioWithDetailsForMigrations =
    TestProcessUtil.wrapWithDetailsForMigration(validScenarioGraph).copy(isArchived = true)

  // TODO: merge with this below
  val sampleScenario: CanonicalProcess = {
    def endWithMessage(idSuffix: String, message: String): SubsequentNode = {
      GraphBuilder
        .buildVariable("message" + idSuffix, "output", "message" -> s"'$message'")
        .emptySink("end" + idSuffix, "kafka-string", TopicParamName -> "'end.topic'", SinkValueParamName -> "#output")
    }
    ScenarioBuilder
      .streaming(ProcessTestData.sampleProcessName.value)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "#input != null")
      .to(endWithMessage("suffix", "message"))
  }

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

  val multipleSourcesValidScenarioGraph: ScenarioGraph = CanonicalProcessConverter.toScenarioGraph(
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

  val scenarioGraphWithInvalidScenarioProperties: ScenarioGraph = ScenarioGraph(
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
    edges = List.empty
  )

  val sampleScenarioGraph: ScenarioGraph = {
    ScenarioGraph(
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
    )
  }

  val sampleFragmentName: ProcessName = ProcessName("fragment1")

  val emptyFragment: CanonicalProcess = {
    CanonicalProcess(MetaData(sampleFragmentName.value, FragmentSpecificData()), List(), List.empty)
  }

  val sampleFragmentOneOut: CanonicalProcess = {
    CanonicalProcess(
      MetaData(sampleFragmentName.value, FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter("param1", FragmentClazzRef[String])))),
        canonicalnode.FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )
  }

  // TODO: Merge with this above
  val sampleFragmentWithInAndOut: CanonicalProcess = CanonicalProcess(
    MetaData(sampleFragmentName.value, FragmentSpecificData()),
    List(
      canonicalnode.FlatNode(
        FragmentInputDefinition("start", List(FragmentParameter("param", FragmentClazzRef[String])))
      ),
      canonicalnode.FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
    ),
    List.empty
  )

  val sampleFragment: CanonicalProcess = {
    CanonicalProcess(
      MetaData(sampleFragmentName.value, FragmentSpecificData()),
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

  val sampleFragment2: CanonicalProcess = {
    CanonicalProcess(
      MetaData(sampleFragmentName.value, FragmentSpecificData()),
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
      comment: Option[UpdateProcessComment]
  ): UpdateScenarioCommand = {
    val properties = ProcessProperties(
      ProcessAdditionalFields(
        description = None,
        properties = Map(
          "maxEvents"        -> "",
          "parallelism"      -> "1",
          "numberOfThreads"  -> "1",
          "spillStateToDisk" -> "true",
          "environment"      -> "test"
        ),
        metaDataType = "StreamMetaData"
      )
    )
    val scenarioGraph = ScenarioGraph(
      properties = properties,
      nodes = List.empty,
      edges = List.empty
    )

    UpdateScenarioCommand(scenarioGraph, comment.getOrElse(UpdateProcessComment("")), None)
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

  final case class ProcessUsingFragment(process: CanonicalProcess, fragment: CanonicalProcess)

  val streamingTypeSpecificInitialData: MetaDataInitializer = MetaDataInitializer(
    StreamMetaData.typeName,
    Map(StreamMetaData.parallelismName -> "1", StreamMetaData.spillStateToDiskName -> "true")
  )

  val sampleSpelTemplateProcess: CanonicalProcess = {
    ScenarioBuilder
      .streaming("sample-spel-template-process")
      .parallelism(1)
      .source("startProcess", "csv-source")
      .to(endWithMessage)
  }

  private def endWithMessage: SubsequentNode = {
    val idSuffix   = "suffix"
    val endMessage = "#test #{#input} #test \n#{\"abc\".toString + {1,2,3}.toString + \"abc\"}\n#test\n#{\"ab{}c\"}"

    GraphBuilder
      .buildVariable("message" + idSuffix, "output", "message" -> spelTemplate(endMessage))
      .emptySink(
        "end" + idSuffix,
        "kafka-string",
        TopicParamName     -> spelTemplate("end.topic"),
        SinkValueParamName -> spelTemplate("#output")
      )
  }

}

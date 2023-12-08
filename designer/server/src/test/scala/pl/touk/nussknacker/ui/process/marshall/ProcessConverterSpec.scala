package pl.touk.nussknacker.ui.process.marshall

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, SpelExpressionExcludeList, StreamMetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue, NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder.{
  wrapWithStaticServiceDefinition,
  wrapWithStaticSourceDefinition
}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.validation.ValidatedDisplayableProcess
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeTypingData,
  NodeValidationError,
  NodeValidationErrorType,
  ValidationResult
}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.sampleResolver
import pl.touk.nussknacker.ui.api.helpers.{StubModelDataWithModelDefinition, TestCategories, TestProcessingTypes}
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser}
import pl.touk.nussknacker.ui.validation.UIProcessValidator

class ProcessConverterSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  private val metaData = StreamMetaData(Some(2), Some(false))

  lazy val validation: UIProcessValidator = {
    val modelDefinition = ModelDefinition[ComponentStaticDefinition](
      services = Map("ref" -> wrapWithStaticServiceDefinition(List.empty, Some(Unknown))),
      sourceFactories = Map("sourceRef" -> wrapWithStaticSourceDefinition(List.empty, Some(Unknown))),
      sinkFactories = Map(),
      customStreamTransformers = Map(),
      expressionConfig = ExpressionDefinition(
        Map.empty,
        List.empty,
        List.empty,
        LanguageConfiguration.default,
        optimizeCompilation = false,
        strictTypeChecking = true,
        Map.empty,
        hideMetaVariable = false,
        strictMethodsChecking = true,
        staticMethodInvocationsChecking = false,
        methodExecutionForUnknownAllowed = false,
        dynamicPropertyAccessAllowed = false,
        spelExpressionExcludeList = SpelExpressionExcludeList.default,
        customConversionsProviders = List.empty
      ),
      settings = ClassExtractionSettings.Default
    )

    new UIProcessValidator(
      ProcessValidator.default(new StubModelDataWithModelDefinition(modelDefinition)),
      FlinkStreamingPropertiesConfig.properties,
      Nil,
      sampleResolver
    )
  }

  def canonicalDisplayable(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    val displayable =
      ProcessConverter.toDisplayable(canonicalProcess, TestProcessingTypes.Streaming, TestCategories.Category1)
    ProcessConverter.fromDisplayable(displayable)
  }

  def displayableCanonical(process: DisplayableProcess): ValidatedDisplayableProcess = {
    implicit val user: LoggedUser = AdminUser("admin", "admin")
    val canonical                 = ProcessConverter.fromDisplayable(process)
    val displayable = ProcessConverter.toDisplayable(canonical, TestProcessingTypes.Streaming, TestCategories.Category1)
    ValidatedDisplayableProcess.withValidationResult(displayable, validation.validate(displayable))
  }

  test("be able to convert empty process") {
    val emptyProcess = CanonicalProcess(MetaData(id = "t1", StreamMetaData()), List(), List.empty)

    canonicalDisplayable(emptyProcess) shouldBe emptyProcess
  }

  test("be able to handle different node order") {
    val process = DisplayableProcess(
      "t1",
      ProcessProperties(metaData),
      List(
        Processor("e", ServiceRef("ref", List())),
        Source("s", SourceRef("sourceRef", List()))
      ),
      List(Edge("s", "e", None)),
      TestProcessingTypes.Streaming,
      TestCategories.Category1
    )

    displayableCanonical(process).nodes.toSet shouldBe process.nodes.toSet
    displayableCanonical(process).edges.toSet shouldBe process.edges.toSet

  }

  test("be able to convert process ending not properly") {
    forAll(
      Table(
        "unexpectedEnd",
        Filter("e", Expression.spel("0")),
        Switch("e"),
        Enricher("e", ServiceRef("ref", List()), "out"),
        Split("e")
      )
    ) { unexpectedEnd =>
      val process = ValidatedDisplayableProcess(
        "t1",
        ProcessProperties(metaData),
        List(Source("s", SourceRef("sourceRef", List())), unexpectedEnd),
        List(Edge("s", "e", None)),
        TestProcessingTypes.Streaming,
        TestCategories.Category1,
        Some(
          ValidationResult.errors(
            Map(
              unexpectedEnd.id -> List(
                NodeValidationError(
                  "InvalidTailOfBranch",
                  "Scenario must end with a sink, processor or fragment",
                  "Scenario must end with a sink, processor or fragment",
                  None,
                  errorType = NodeValidationErrorType.SaveAllowed
                )
              )
            ),
            List.empty,
            List.empty
          )
        )
      )

      val validated = displayableCanonical(process.toDisplayable)
      val withoutTypes =
        validated.copy(validationResult = validated.validationResult.map(_.copy(nodeResults = Map.empty)))
      withoutTypes shouldBe process
    }
  }

  test("return variable type information for process that cannot be canonized") {
    val meta = MetaData.combineTypeSpecificProperties(
      id = "process",
      typeSpecificData = metaData,
      additionalFields = ProcessAdditionalFields(None, Map.empty, StreamMetaData.typeName)
    )
    val process = ValidatedDisplayableProcess(
      meta.id,
      ProcessProperties(meta.typeSpecificData),
      List(
        Source("s", SourceRef("sourceRef", List())),
        Variable("v", "test", Expression.spel("''")),
        Filter("e", Expression.spel("''"))
      ),
      List(Edge("s", "v", None), Edge("v", "e", None)),
      TestProcessingTypes.Streaming,
      TestCategories.Category1,
      Some(
        ValidationResult
          .errors(
            Map(
              "e" -> List(
                NodeValidationError(
                  "InvalidTailOfBranch",
                  "Scenario must end with a sink, processor or fragment",
                  "Scenario must end with a sink, processor or fragment",
                  None,
                  errorType = NodeValidationErrorType.SaveAllowed
                )
              )
            ),
            List.empty,
            List.empty
          )
          .copy(nodeResults =
            Map(
              "s" -> NodeTypingData(Map.empty, Some(List.empty), Map.empty),
              "v" -> NodeTypingData(
                Map("input" -> Unknown),
                None,
                Map.empty
              ),
              "e" -> NodeTypingData(
                Map("input" -> Unknown, "test" -> Typed.fromInstance("")),
                None,
                Map.empty
              )
            )
          )
      )
    )

    val displayableProcess = displayableCanonical(process.toDisplayable)
    // because I'm lazy
    val displayableWithClearedTypingInfo = displayableProcess.copy(
      validationResult = displayableProcess.validationResult.map(validationResult =>
        validationResult.copy(
          nodeResults = validationResult.nodeResults.mapValuesNow(_.copy(typingInfo = Map.empty))
        )
      )
    )
    displayableWithClearedTypingInfo shouldBe process
  }

  test("convert process with branches") {

    val process = DisplayableProcess(
      "t1",
      ProcessProperties(metaData),
      List(
        Processor("e", ServiceRef("ref", List.empty)),
        Join("j1", Some("out1"), "joinRef", List.empty, List(BranchParameters("s1", List()))),
        Source("s2", SourceRef("sourceRef", List.empty)),
        Source("s1", SourceRef("sourceRef", List.empty))
      ),
      List(
        Edge("s1", "j1", None),
        Edge("s2", "j1", None),
        Edge("j1", "e", None)
      ),
      TestProcessingTypes.Streaming,
      TestCategories.Category1
    )

    val processViaBuilder = ScenarioBuilder
      .streaming("t1")
      .parallelism(metaData.parallelism.get)
      .stateOnDisk(metaData.spillStateToDisk.get)
      .sources(
        GraphBuilder.join("j1", "joinRef", Some("out1"), List("s1" -> List())).processorEnd("e", "ref"),
        GraphBuilder.source("s2", "sourceRef").branchEnd("s2", "j1"),
        GraphBuilder.source("s1", "sourceRef").branchEnd("s1", "j1")
      )

    displayableCanonical(process).nodes.sortBy(_.id) shouldBe process.nodes.sortBy(_.id)
    displayableCanonical(process).edges.toSet shouldBe process.edges.toSet

    val canonical = ProcessConverter.fromDisplayable(process)

    canonical shouldBe processViaBuilder
  }

  test("Convert branches to displayable") {
    import pl.touk.nussknacker.engine.spel.Implicits._

    val process = ScenarioBuilder
      .streamingLite("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "sourceType1")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "sourceType1")
          .filter("filter2", "false")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join("join1", "union", Some("outPutVar"), List("branch1" -> Nil, "branch2" -> Nil))
          .emptySink("end", "outType1")
      )

    val displayableProcess = ProcessConverter.toDisplayable(process, "type1", TestCategories.Category1)

    displayableProcess.edges.toSet shouldBe Set(
      Edge("sourceId1", "join1", None),
      Edge("sourceId2", "filter2", None),
      Edge("filter2", "join1", Some(FilterTrue)),
      Edge("join1", "end", None)
    )

  }

  test("finds all nodes in diamond-shaped process") {

    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "sourceType1")
          .split("split1", GraphBuilder.branchEnd("branch1", "join1"), GraphBuilder.branchEnd("branch2", "join1")),
        GraphBuilder
          .join("join1", "union", Some("outPutVar"), List("branch1" -> Nil, "branch2" -> Nil))
          .emptySink("end", "outType1")
      )

    val foundNodes = ProcessConverter.findNodes(process)

    foundNodes.map(_.id).toSet shouldBe Set("sourceId1", "split1", "join1", "end")

  }

  test("Handle switch/split/filter => union case") {

    val branchEnd      = GraphBuilder.branchEnd("branch1", "join1")
    val nodeId: String = "problemNode"

    def testCase(
        run: GraphBuilder[SourceNode] => SourceNode,
        typ: Option[EdgeType] = None,
        additionalEdges: Set[Edge] = Set.empty
    ) = {
      val process = ScenarioBuilder
        .streaming("proc1")
        .sources(
          run(
            GraphBuilder
              .source("source1", "sourceType1")
          ),
          GraphBuilder
            .join("join1", "union", Some("outPutVar"), List("branch1" -> Nil, "branch2" -> Nil))
            .emptySink("end", "outType1")
        )
      val edges = ProcessConverter.toDisplayable(process, "", TestCategories.Category1).edges
      edges.toSet shouldBe Set(
        Edge("source1", nodeId, None),
        Edge(nodeId, "join1", typ),
        Edge("join1", "end", None)
      ) ++ additionalEdges
    }

    testCase(_.split(nodeId, branchEnd))
    testCase(
      _.filter(nodeId, "false", branchEnd).emptySink("end2", "out1"),
      Some(FilterFalse),
      Set(Edge(nodeId, "end2", Some(FilterTrue)))
    )
    testCase(_.switch(nodeId, "false", "out1", Case("1", branchEnd)), Some(NextSwitch("1")))
    testCase(
      _.switch(nodeId, "false", "out1", branchEnd, Case("1", GraphBuilder.emptySink("end2", "out1"))),
      Some(SwitchDefault),
      Set(Edge(nodeId, "end2", Some(NextSwitch("1"))))
    )

  }

}

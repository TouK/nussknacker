package pl.touk.nussknacker.engine.requestresponse

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.ConfigFactory
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ComponentUseContextProvider
import pl.touk.nussknacker.engine.api.{Context, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.components.LiteBaseComponentProvider
import pl.touk.nussknacker.engine.lite.components.requestresponse.RequestResponseComponentProvider
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.metrics.common.naming.scenarioIdTag
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.util
import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.util.Using

class RequestResponseInterpreterSpec extends AnyFunSuite with Matchers with PatientScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("run scenario in request response mode") {

    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'".spel)
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1".spel)

    val creator   = new RequestResponseSampleComponents
    val contextId = firstIdForFirstSource(scenario)
    val result    = runScenario(scenario, Request1("a", "b"), creator)

    result shouldBe Valid(List(Response(s"alamakota-$contextId")))
    creator.processorService.invocationsCount.get() shouldBe 1
  }

  test("collect results after split") {
    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .split(
        "split",
        GraphBuilder.emptySink("sink1", "response-sink", "value" -> "#input.field1".spel),
        GraphBuilder.emptySink("sink2", "response-sink", "value" -> "#input.field2".spel)
      )

    val result = runScenario(scenario, Request1("a", "b"))

    result.validValue.toSet shouldBe Set("a", "b")
  }

  test("collect metrics") {

    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'".spel)
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1".spel)

    val creator        = new RequestResponseSampleComponents
    val metricRegistry = new MetricRegistry

    Using.resource(prepareInterpreter(scenario, creator, metricRegistry)) { interpreter =>
      interpreter.open()
      val contextId = firstIdForFirstSource(scenario)
      val result    = invokeInterpreter(interpreter, Request1("a", "b"))

      result shouldBe Valid(List(Response(s"alamakota-$contextId")))
      creator.processorService.invocationsCount.get() shouldBe 1

      eventually {
        metricRegistry
          .getGauges()
          .get(
            MetricRegistry
              .name("invocation", "success", "instantRate")
              .tagged(scenarioIdTag, "proc1")
          )
          .getValue
          .asInstanceOf[Double] should not be 0
        metricRegistry
          .getHistograms()
          .get(
            MetricRegistry
              .name("invocation", "success", "histogram")
              .tagged(scenarioIdTag, "proc1")
          )
          .getCount shouldBe 1
      }
    }
  }

  test("collect results after element split") {
    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .customNode("for-each", "outPart", "for-each", "Elements" -> "#input.toList()".spel)
      .buildSimpleVariable("var1", "var1", "#outPart".spel)
      .emptySink("sink1", "response-sink", "value" -> "#outPart".spel)

    val result = runScenario(scenario, Request1("a", "b"))

    result shouldBe Valid(List("a", "b"))
  }

  test("init call open method for service") {
    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .emptySink("sink1", "response-sink", "value" -> "#response.field1".spel)

    val result = runScenario(scenario, Request1("a", "b"))

    result shouldBe Valid(List("true"))
  }

  test("init call open method for eager service") {
    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .enricher("enricher1", "response1", "eagerEnricherWithOpen", "name" -> "'1'".spel)
      .customNode("custom", "output", "extractor", "expression" -> "''".spel)
      .enricher("enricher2", "response2", "eagerEnricherWithOpen", "name" -> "'2'".spel)
      .emptySink("sink1", "response-sink", "value" -> "#response1.field1 + #response2.field1".spel)

    val creator = new RequestResponseSampleComponents
    val result  = runScenario(scenario, Request1("a", "b"), creator)

    result shouldBe Valid(List("truetrue"))
    creator.eagerEnricher.opened shouldBe true
    creator.eagerEnricher.closed shouldBe true
    val openedInvokers = creator.eagerEnricher.list.filter(_._2.opened == true)
    openedInvokers.map(_._1) should contain only ("1", "2")
    openedInvokers.foreach { cl =>
      cl._2.closed shouldBe true
    }

  }

  test("collect metrics for individual services") {
    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .emptySink("sink1", "response-sink", "value" -> "#response.field1".spel)

    val metricRegistry = new MetricRegistry

    Using.resource(
      prepareInterpreter(scenario, new RequestResponseSampleComponents, metricRegistry = metricRegistry)
    ) { interpreter =>
      interpreter.open()
      val result = invokeInterpreter(interpreter, Request1("a", "b"))

      result shouldBe Valid(List("true"))

      eventually {

        metricRegistry
          .getGauges()
          .get(
            MetricRegistry
              .name("invocation", "success", "instantRate")
              .tagged(scenarioIdTag, "proc1")
          )
          .getValue
          .asInstanceOf[Double] should not be 0
        metricRegistry
          .getHistograms()
          .get(
            MetricRegistry
              .name("invocation", "success", "histogram")
              .tagged(scenarioIdTag, "proc1")
          )
          .getCount shouldBe 1
        metricRegistry
          .getGauges()
          .get(
            MetricRegistry
              .name("service", "OK", "instantRate")
              .tagged(scenarioIdTag, "proc1", "serviceName", "enricherWithOpenService")
          )
          .getValue
          .asInstanceOf[Double] should not be 0
        metricRegistry
          .getHistograms()
          .get(
            MetricRegistry
              .name("service", "OK", "histogram")
              .tagged(scenarioIdTag, "proc1", "serviceName", "enricherWithOpenService")
          )
          .getCount shouldBe 1
      }
    }
  }

  test("run scenario using custom node with ContextTransformation API") {
    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .customNode("extract", "extracted", "extractor", "expression" -> "#input.field2".spel)
      .emptySink("sink1", "response-sink", "value" -> "#extracted".spel)

    val result = runScenario(scenario, Request1("a", "b"))

    result shouldBe Valid(List("b"))
  }

  test("collects answers from parameters") {
    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'".spel)

    val result = runScenario(scenario, Request1("abc", "b"))

    result shouldBe Valid(List("abcd withRandomString"))
  }

  test("recognizes output types") {

    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'".spel)

    val interpreter = prepareInterpreter(scenario = scenario)
    interpreter.sinkTypes shouldBe Map(NodeId("endNodeIID") -> Typed[String])

    val process2 = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "response-sink", "value" -> "{'str': #input.toString(), 'int': 15}".spel)

    val interpreter2 = prepareInterpreter(scenario = process2)
    interpreter2.sinkTypes shouldBe Map(
      NodeId("endNodeIID") -> Typed.record(ListMap("str" -> Typed[String], "int" -> Typed.fromInstance(15)))
    )

  }

  test("handles exceptions in sink") {
    val scenario = ScenarioBuilder
      .requestResponse("exception-in-sink")
      .source("start", "request1-post-source")
      .emptySink("sinkId", "failing-sink", "fail" -> "true".spel)

    val creator   = new RequestResponseSampleComponents
    val contextId = firstIdForFirstSource(scenario)
    val result    = runScenario(scenario, Request1("a", "b"), creator, contextId = Some(contextId))

    result shouldBe Invalid(
      NonEmptyList.of(
        NuExceptionInfo(
          Some(NodeComponentInfo("sinkId", ComponentType.Sink, "unknown")),
          SinkException("FailingSink failed"),
          Context(contextId, Map("input" -> Request1("a", "b")), None)
        )
      )
    )
  }

  test("ignore filter and continue scenario execution") {
    val scenario = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .customNodeNoOutput("filter", "customFilter", "filterExpression" -> "true".spel)
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'".spel)

    val result = runScenario(scenario, Request1("abc", "b"))

    result shouldBe Valid(List("abcd withRandomString"))
  }

  test("should perform union") {
    val scenario = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "request1-post-source")
          .split(
            "spl",
            GraphBuilder.buildSimpleVariable("v1", "v1", "'aa'".spel).branchEnd("branch1", "join1"),
            GraphBuilder.buildSimpleVariable("v1a", "v2", "'bb'".spel).branchEnd("branch1a", "join1")
          ),
        GraphBuilder
          .join(
            "join1",
            "union",
            Some("unionOutput"),
            List(
              "branch1"  -> List("Output expression" -> "{a: #v1}".spel),
              "branch1a" -> List("Output expression" -> "{a: #v2}".spel)
            )
          )
          .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#unionOutput.a".spel)
      )

    val result = runScenario(scenario, Request1("abc", "b"))
    result shouldBe Valid(List("aa withRandomString", "bb withRandomString"))
  }

  test("should sort split results") {

    val scenario = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "request1-post-source")
          .split(
            "spl",
            (1 to 5).map(v =>
              GraphBuilder
                .buildVariable(s"var$v", "v1", "value" -> s"'v$v'".spel, "rank" -> v.toString.spel)
                .branchEnd(s"branch$v", "joinWithSort")
            ): _*
          ),
        GraphBuilder
          .join(
            "joinWithSort",
            "union",
            Some("unionOutput"),
            List(
              "branch1" -> List("Output expression" -> "#v1".spel),
              "branch2" -> List("Output expression" -> "#v1".spel),
              "branch3" -> List("Output expression" -> "#v1".spel),
              "branch4" -> List("Output expression" -> "#v1".spel),
              "branch5" -> List("Output expression" -> "#v1".spel)
            )
          )
          .customNode(
            "sorter",
            "sorted",
            "sorter",
            "maxCount" -> "2".spel,
            "rank"     -> "#unionOutput.rank".spel,
            "output"   -> "#unionOutput.value".spel
          )
          .emptySink("endNodeIID", "response-sink", "value" -> "#sorted".spel)
      )

    val result = runScenario(scenario, Request1("abc", "b"))
    result shouldBe Valid(List(util.Arrays.asList("v5", "v4")))
  }

  def runScenario(
      scenario: CanonicalProcess,
      input: Any,
      creator: RequestResponseSampleComponents = new RequestResponseSampleComponents,
      metricRegistry: MetricRegistry = new MetricRegistry,
      contextId: Option[String] = None
  ): ValidatedNel[ErrorType, List[Any]] =
    Using.resource(
      prepareInterpreter(
        scenario = scenario,
        sampleComponents = creator,
        metricRegistry = metricRegistry
      )
    ) { interpreter =>
      interpreter.open()
      invokeInterpreter(interpreter, input)
    }

  def prepareInterpreter(
      scenario: CanonicalProcess,
      sampleComponents: RequestResponseSampleComponents,
      metricRegistry: MetricRegistry
  ): InterpreterType = {
    prepareInterpreter(
      scenario,
      sampleComponents,
      new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))
    )
  }

  def prepareInterpreter(
      scenario: CanonicalProcess,
      sampleComponents: RequestResponseSampleComponents = new RequestResponseSampleComponents,
      engineRuntimeContextPreparer: LiteEngineRuntimeContextPreparer = LiteEngineRuntimeContextPreparer.noOp
  ): InterpreterType = {
    val simpleModelData =
      LocalModelData(
        ConfigFactory.load(),
        sampleComponents.components :::
          RequestResponseComponentProvider.Components ::: LiteBaseComponentProvider.Components
      )

    import FutureBasedRequestResponseScenarioInterpreter._
    val maybeinterpreter = RequestResponseInterpreter[Future](
      scenario,
      ProcessVersion.empty,
      engineRuntimeContextPreparer,
      simpleModelData,
      Nil,
      ProductionServiceInvocationCollector,
      ComponentUseContextProvider.LiveRuntime
    )

    maybeinterpreter shouldBe Symbol("valid")
    val interpreter = maybeinterpreter.toOption.get
    interpreter
  }

  private def invokeInterpreter(interpreter: InterpreterType, input: Any) = {
    interpreter.invokeToOutput(input).futureValue
  }

  private def firstIdForFirstSource(scenario: CanonicalProcess): String =
    IncContextIdGenerator.withProcessIdNodeIdPrefix(scenario.metaData, scenario.nodes.head.id).nextContextId()

}

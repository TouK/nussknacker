package pl.touk.nussknacker.engine.requestresponse

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.ConfigFactory
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.{Context, MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.requestresponse.test.RunnableEspScenario
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.metrics.common.naming.scenarioIdTag
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util
import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.util.Using

class RequestResponseInterpreterSpec extends FunSuite with RunnableEspScenario {

  import scala.concurrent.ExecutionContext.Implicits.global

  test("run scenario in request response mode") {

    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1")

    val creator = new RequestResponseConfigCreator
    val contextId = scenario.contextIdGenerator.nextContextId()
    val result = scenario.runTestWith(Request1("a", "b"), creator)

    result shouldBe Valid(List(Response(s"alamakota-$contextId")))
    creator.processorService.invocationsCount.get() shouldBe 1
  }

  test("collect results after split") {
    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .split("split",
        GraphBuilder.emptySink("sink1", "response-sink", "value" -> "#input.field1"),
        GraphBuilder.emptySink("sink2", "response-sink", "value" -> "#input.field2")
      )

    val result = scenario.runTestWith(Request1("a", "b"))

    result shouldBe Valid(List("a", "b"))
  }

  test("collect metrics") {

    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1")

    val creator = new RequestResponseConfigCreator
    val metricRegistry = new MetricRegistry

    Using.resource(scenario.prepareInterpreter(creator, metricRegistry)) { interpreter =>
      interpreter.open()
      val contextId = scenario.contextIdGenerator.nextContextId()
      val result = invokeInterpreter(interpreter, Request1("a", "b"))

      result shouldBe Valid(List(Response(s"alamakota-$contextId")))
      creator.processorService.invocationsCount.get() shouldBe 1

      eventually {
        metricRegistry.getGauges().get(MetricRegistry.name("invocation", "success", "instantRate")
          .tagged(scenarioIdTag, "proc1")).getValue.asInstanceOf[Double] should not be 0
        metricRegistry.getHistograms().get(MetricRegistry.name("invocation", "success", "histogram")
          .tagged(scenarioIdTag, "proc1")).getCount shouldBe 1
      }
    }
  }

  test("collect results after element split") {
    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .customNode("for-each", "outPart", "for-each", "Elements" -> "#input.toList()")
      .buildSimpleVariable("var1", "var1", "#outPart")
      .emptySink("sink1", "response-sink", "value" -> "#outPart")

    val result = scenario.runTestWith(Request1("a", "b"))

    result shouldBe Valid(List("a", "b"))
  }

  test("init call open method for service") {
    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .emptySink("sink1", "response-sink", "value" -> "#response.field1")

    val result = scenario.runTestWith(Request1("a", "b"))

    result shouldBe Valid(List("true"))
  }

  test("init call open method for eager service") {
    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .enricher("enricher1", "response1", "eagerEnricherWithOpen", "name" -> "'1'")
      .customNode("custom", "output", "extractor", "expression" -> "''")
      .enricher("enricher2", "response2", "eagerEnricherWithOpen", "name" -> "'2'")
      .emptySink("sink1", "response-sink", "value" -> "#response1.field1 + #response2.field1")

    val creator = new RequestResponseConfigCreator
    val result = scenario.runTestWith(Request1("a", "b"), creator)

    result shouldBe Valid(List("truetrue"))
    creator.eagerEnricher.opened shouldBe true
    creator.eagerEnricher.closed shouldBe true
    val openedInvokers = creator.eagerEnricher.list.filter(_._2.opened == true)
    openedInvokers.map(_._1).toSet == Set("1", "2")
    openedInvokers.foreach { cl =>
      cl._2.closed shouldBe true
    }

  }

  test("collect metrics for individual services") {
    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .emptySink("sink1", "response-sink", "value" -> "#response.field1")

    val metricRegistry = new MetricRegistry

    Using.resource(
      scenario.prepareInterpreter(new RequestResponseConfigCreator, metricRegistry = metricRegistry)
    ) { interpreter =>
      interpreter.open()
      val result = invokeInterpreter(interpreter, Request1("a", "b"))

      result shouldBe Valid(List("true"))

      eventually {

        metricRegistry.getGauges().get(MetricRegistry.name("invocation", "success", "instantRate")
          .tagged(scenarioIdTag, "proc1")).getValue.asInstanceOf[Double] should not be 0
        metricRegistry.getHistograms().get(MetricRegistry.name("invocation", "success", "histogram")
          .tagged(scenarioIdTag, "proc1")).getCount shouldBe 1
        metricRegistry.getGauges().get(MetricRegistry.name("service", "OK", "instantRate")
          .tagged(scenarioIdTag, "proc1", "serviceName", "enricherWithOpenService")).getValue.asInstanceOf[Double] should not be 0
        metricRegistry.getHistograms().get(MetricRegistry.name("service", "OK", "histogram")
          .tagged(scenarioIdTag, "proc1", "serviceName", "enricherWithOpenService")).getCount shouldBe 1
      }
    }
  }

  test("run scenario using custom node with ContextTransformation API") {
    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .customNode("extract", "extracted", "extractor", "expression" -> "#input.field2")
      .emptySink("sink1", "response-sink", "value" -> "#extracted")

    val result = scenario.runTestWith(Request1("a", "b"))

    result shouldBe Valid(List("b"))
  }

  test("collects answers from parameters") {
    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'")

    val result = scenario.runTestWith(Request1("abc", "b"))

    result shouldBe Valid(List("abcd withRandomString"))
  }

  test("recognizes output types") {

    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'")


    val interpreter = scenario.prepareInterpreter()
    interpreter.sinkTypes shouldBe Map(NodeId("endNodeIID") -> Typed[String])

    val scenario2 = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "response-sink", "value" -> "{'str': #input.toString(), 'int': 15}")


    val interpreter2 = scenario2.prepareInterpreter()
    interpreter2.sinkTypes shouldBe Map(NodeId("endNodeIID") -> TypedObjectTypingResult(ListMap("str" -> Typed[String], "int" -> Typed[java.lang.Integer])))

  }

  test("handles exceptions in sink") {
    val scenario = ScenarioBuilder
      .streaming("exception-in-sink")
      .source("start", "request1-post-source")
      .emptySink("sinkId", "failing-sink", "fail" -> "true")

    val creator = new RequestResponseConfigCreator
    val contextId = scenario.contextIdGenerator.nextContextId()
    val result = scenario.runTestWith(Request1("a", "b"), creator, contextId = Some(contextId))

    result shouldBe Invalid(NonEmptyList.of(
      NuExceptionInfo(Some(NodeComponentInfo("sinkId", "unknown", ComponentType.Sink)),
        SinkException("FailingSink failed"),
        Context(contextId, Map("input" -> Request1("a", "b")), None))
    ))
  }

  test("ignore filter and continue scenario execution") {
    val scenario = ScenarioBuilder
      .streaming("proc1")
      .source("start", "request1-post-source")
      .customNodeNoOutput("filter", "customFilter", "filterExpression" -> "true")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'")

    val result = scenario.runTestWith(Request1("abc", "b"))

    result shouldBe Valid(List("abcd withRandomString"))
  }

  test("should perform union") {
    val scenario = EspProcess(MetaData("proc1", StreamMetaData()), NonEmptyList.of(
      GraphBuilder
        .source("sourceId1", "request1-post-source")
        .split("spl",
          GraphBuilder.buildSimpleVariable("v1", "v1", "'aa'").branchEnd("branch1", "join1"),
          GraphBuilder.buildSimpleVariable("v1a", "v2", "'bb'").branchEnd("branch1a", "join1")),
      GraphBuilder
        .join("join1", "union", Some("unionOutput"),
          List(
            "branch1" -> List("Output expression" -> "{a: #v1}"),
            "branch1a" -> List("Output expression" -> "{a: #v2}"))
        )
        .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#unionOutput.a")
    ))

    val result = scenario.runTestWith(Request1("abc", "b"))
    result shouldBe Valid(List("aa withRandomString", "bb withRandomString"))
  }

  test("should sort split results") {

    val scenario = EspProcess(MetaData("proc1", StreamMetaData()), NonEmptyList.of(
      GraphBuilder
        .source("sourceId1", "request1-post-source")
        .split("spl",
          (1 to 5).map(v => GraphBuilder.buildVariable(s"var$v", "v1", "value" -> s"'v$v'", "rank" -> v.toString)
            .branchEnd(s"branch$v", "joinWithSort")): _*),
      GraphBuilder
      .join("joinWithSort", "union", Some("unionOutput"),
        List(
          "branch1" -> List("Output expression" -> "#v1"),
          "branch2" -> List("Output expression" -> "#v1"),
          "branch3" -> List("Output expression" -> "#v1"),
          "branch4" -> List("Output expression" -> "#v1"),
          "branch5" -> List("Output expression" -> "#v1"))
      )

      .customNode("sorter", "sorted", "sorter",
          "maxCount" -> "2", "rank" -> "#unionOutput.rank", "output" -> "#unionOutput.value")
        .emptySink("endNodeIID", "response-sink", "value" -> "#sorted")
    ))

    val result = scenario.runTestWith(Request1("abc", "b"))
    result shouldBe Valid(List(util.Arrays.asList("v5", "v4")))
  }

}

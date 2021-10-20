package pl.touk.nussknacker.engine.standalone

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.ConfigFactory
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.{Context, JobData, MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.baseengine.api.metrics.MetricsProvider
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.EngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.baseengine.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.baseengine.metrics.dropwizard.DropwizardMetricsProvider
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util
import scala.collection.immutable.ListMap
import scala.util.Using

class StandaloneProcessInterpreterSpec extends FunSuite with Matchers with PatientScalaFutures {

  import spel.Implicits._

  import scala.concurrent.ExecutionContext.Implicits.global


  test("run process in request response mode") {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1")

    val creator = new StandaloneProcessConfigCreator
    val contextId = "context-id"
    val result = runProcess(process, Request1("a", "b"), creator, contextId = Some(contextId))

    result shouldBe Valid(List(Response(s"alamakota-$contextId")))
    creator.processorService.invocationsCount.get() shouldBe 1
  }

  test("collect results after split") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .split("split",
        GraphBuilder.emptySink("sink1", "response-sink", "value" -> "#input.field1"),
        GraphBuilder.emptySink("sink2", "response-sink", "value" -> "#input.field2")
      )

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Valid(List("a", "b"))
  }

  test("collect metrics") {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1")

    val creator = new StandaloneProcessConfigCreator
    val metricRegistry = new MetricRegistry

    Using.resource(prepareInterpreter(process, creator, metricRegistry)) { interpreter =>
      interpreter.open(JobData(process.metaData, ProcessVersion.empty, DeploymentData.empty))
      val contextId = "context-id"
      val result = interpreter.invokeToOutput(Request1("a", "b"), Some(contextId)).futureValue

      result shouldBe Valid(List(Response(s"alamakota-$contextId")))
      creator.processorService.invocationsCount.get() shouldBe 1

      eventually {
        metricRegistry.getGauges().get(MetricRegistry.name("invocation", "success", "instantRate")
          .tagged("processId", "proc1")).getValue.asInstanceOf[Double] should not be 0
        metricRegistry.getHistograms().get(MetricRegistry.name("invocation", "success", "histogram")
          .tagged("processId", "proc1")).getCount shouldBe 1
      }
    }
  }

  test("collect results after element split") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .customNode("split", "outPart", "splitter", "parts" -> "#input.toList()")
      .buildSimpleVariable("var1", "var1", "#outPart")
      .emptySink("sink1", "response-sink", "value" -> "#outPart")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Valid(List("a", "b"))
  }

  test("init call open method for service") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .emptySink("sink1", "response-sink", "value" -> "#response.field1")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Valid(List("true"))
  }

  test("init call open method for eager service") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .enricher("enricher1", "response1", "eagerEnricherWithOpen", "name" -> "'1'")
      .customNode("custom", "output", "extractor", "expression" -> "''")
      .enricher("enricher2", "response2", "eagerEnricherWithOpen", "name" -> "'2'")
      .emptySink("sink1", "response-sink", "value" -> "#response1.field1 + #response2.field1")

    val creator = new StandaloneProcessConfigCreator
    val result = runProcess(process, Request1("a", "b"), creator)

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
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .emptySink("sink1", "response-sink", "value" -> "#response.field1")

    val metricRegistry = new MetricRegistry

    Using.resource(
      prepareInterpreter(process, new StandaloneProcessConfigCreator, metricRegistry = metricRegistry)
    ) { interpreter =>
      interpreter.open(JobData(process.metaData, ProcessVersion.empty, DeploymentData.empty))
      val result = interpreter.invokeToOutput(Request1("a", "b")).futureValue

      result shouldBe Valid(List("true"))

      eventually {
        metricRegistry.getGauges().get(MetricRegistry.name("invocation", "success", "instantRate")
          .tagged("processId", "proc1")).getValue.asInstanceOf[Double] should not be 0
        metricRegistry.getHistograms().get(MetricRegistry.name("invocation", "success", "histogram")
          .tagged("processId", "proc1")).getCount shouldBe 1
        metricRegistry.getGauges().get(MetricRegistry.name("service", "OK", "instantRate")
          .tagged("processId", "proc1", "serviceName", "enricherWithOpenService")).getValue.asInstanceOf[Double] should not be 0
        metricRegistry.getHistograms().get(MetricRegistry.name("service", "OK", "histogram")
          .tagged("processId", "proc1", "serviceName", "enricherWithOpenService")).getCount shouldBe 1
      }
    }
  }

  test("run process using custom node with ContextTransformation API") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .customNode("extract", "extracted", "extractor", "expression" -> "#input.field2")
      .emptySink("sink1", "response-sink", "value" -> "#extracted")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Valid(List("b"))
  }

  test("collects answers from parameters") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'")

    val result = runProcess(process, Request1("abc", "b"))

    result shouldBe Valid(List("abcd withRandomString"))
  }

  test("recognizes output types") {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'")


    val interpreter = prepareInterpreter(process = process)
    interpreter.sinkTypes shouldBe Map(NodeId("endNodeIID") -> Typed[String])

    val process2 = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "response-sink", "value" -> "{'str': #input.toString(), 'int': 15}")


    val interpreter2 = prepareInterpreter(process = process2)
    interpreter2.sinkTypes shouldBe Map(NodeId("endNodeIID") -> TypedObjectTypingResult(ListMap("str" -> Typed[String], "int" -> Typed[java.lang.Integer])))

  }

  test("handles exceptions in sink") {
    val process = EspProcessBuilder
      .id("exception-in-sink")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .emptySink("sink", "failing-sink", "fail" -> "true")

    val creator = new StandaloneProcessConfigCreator
    val contextId = "context-id"
    val result = runProcess(process, Request1("a", "b"), creator, contextId = Some(contextId))

    result shouldBe Invalid(NonEmptyList.of(
      EspExceptionInfo(Some("sink"),
        SinkException("FailingSink failed"),
        Context("context-id", Map("input" -> Request1("a", "b")), None))
    ))
  }

  test("ignore filter and continue process execution") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .customNodeNoOutput("filter", "customFilter", "filterExpression" -> "true")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'")

    val result = runProcess(process, Request1("abc", "b"))

    result shouldBe Valid(List("abcd withRandomString"))
  }

  test("should perform union") {
    val process = EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
      GraphBuilder
        .source("sourceId1", "request1-post-source")
        .split("spl",
          GraphBuilder.buildSimpleVariable("v1", "v1", "'aa'").branchEnd("branch1", "join1"),
          GraphBuilder.buildSimpleVariable("v1a", "v1", "'bb'").branchEnd("branch1a", "join1")),
      GraphBuilder
        .branch("join1", "union", None, Nil)
        .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + ' ' + #v1")
    ))

    val result = runProcess(process, Request1("abc", "b"))
    result shouldBe Valid(List("abc aa withRandomString", "abc bb withRandomString"))
  }

  test("should sort split results") {

    val process = EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
      GraphBuilder
        .source("sourceId1", "request1-post-source")
        .split("spl",
          (1 to 5).map(v => GraphBuilder.buildVariable(s"var$v", "v1", "value" -> s"'v$v'", "rank" -> v.toString)
            .branchEnd(s"branch$v", "joinWithSort")): _*),
      GraphBuilder
        .branch("joinWithSort", "union", None, Nil)
        .customNode("sorter", "sorted", "sorter",
          "maxCount" -> "2", "rank" -> "#v1.rank", "output" -> "#v1.value")
        .emptySink("endNodeIID", "response-sink", "value" -> "#sorted")
    ))

    val result = runProcess(process, Request1("abc", "b"))
    result shouldBe Valid(List(util.Arrays.asList("v5", "v4")))
  }

  test("render schema for process") {
    val schema = "'{\"properties\": {\"city\": {\"type\": \"string\", \"default\": \"Warsaw\"}}}'"
    val process = EspProcessBuilder
      .id("proc1")
      .additionalFields(properties = Map("paramName" -> "paramValue"))
      .exceptionHandler()
      .source("start", "jsonSchemaSource", "schema" -> schema)
      .emptySink("endNodeIID", "response-sink", "value" -> "#input")

    val interpreter = prepareInterpreter(process = process)
    val openApiOpt = interpreter.generateOpenApiDefinition()
    val expectedOpenApi =
      """{
        |  "post" : {
        |    "description" : "**paramName**: paramValue",
        |    "tags" : [
        |      "Nussknacker"
        |    ],
        |    "requestBody" : {
        |      "required" : true,
        |      "content" : {
        |        "application/json" : {
        |          "schema" : {
        |            "properties" : {
        |              "city" : {
        |                "type" : "string",
        |                "default" : "Warsaw"
        |              }
        |            }
        |          }
        |        }
        |      }
        |    },
        |    "produces" : [
        |      "application/json"
        |    ],
        |    "consumes" : [
        |      "application/json"
        |    ],
        |    "summary" : "proc1",
        |    "responses" : {
        |      "200" : {
        |        "content" : {
        |          "application/json" : {
        |            "schema" : {
        |              "type" : "object",
        |              "properties" : null
        |            }
        |          }
        |        }
        |      }
        |    }
        |  }
        |}""".stripMargin

    openApiOpt shouldBe defined
    openApiOpt.get.spaces2 shouldBe expectedOpenApi
  }

  def runProcess(process: EspProcess,
                 input: Any,
                 creator: StandaloneProcessConfigCreator = new StandaloneProcessConfigCreator,
                 metricRegistry: MetricRegistry = new MetricRegistry,
                 contextId: Option[String] = None): ValidatedNel[ErrorType, Any] =
    Using.resource(prepareInterpreter(
      process = process,
      creator = creator,
      metricRegistry = metricRegistry
    )) { interpreter =>
      interpreter.open(JobData(process.metaData, ProcessVersion.empty, DeploymentData.empty))
      interpreter.invokeToOutput(input, contextId).futureValue
    }

  def prepareInterpreter(process: EspProcess,
                         creator: StandaloneProcessConfigCreator,
                         metricRegistry: MetricRegistry): StandaloneScenarioEngine.StandaloneScenarioInterpreter = {
    prepareInterpreter(process, creator, new DropwizardMetricsProvider(metricRegistry))
  }

  def prepareInterpreter(process: EspProcess,
                         creator: StandaloneProcessConfigCreator = new StandaloneProcessConfigCreator,
                         metricsProvider: MetricsProvider = NoOpMetricsProvider): StandaloneScenarioEngine.StandaloneScenarioInterpreter = {
    val simpleModelData = LocalModelData(ConfigFactory.load(), creator)
    val ctx = new EngineRuntimeContextPreparer(metricsProvider)

    val maybeinterpreter = StandaloneScenarioEngine(process, ctx, simpleModelData, Nil, ProductionServiceInvocationCollector, RunMode.Normal)

    maybeinterpreter shouldBe 'valid
    val interpreter = maybeinterpreter.toOption.get
    interpreter
  }

}

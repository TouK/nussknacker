package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, ExpressionParserCompilationError}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, JobData, MetaData, ProcessVersion, VariableConstants}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.compile.{CompilationResult, FragmentResolver, ProcessValidator}
import pl.touk.nussknacker.engine.definition.component.parameter.editor.ParameterTypeEditorDeterminer
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{CustomNode, FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder, TestProcess}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.config.DocsConfig

import java.time.{Duration, OffsetDateTime}
import java.util
import java.util.Arrays.asList
import scala.jdk.CollectionConverters._

class TransformersTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  def modelData(
      list: List[TestRecord] = List(),
      aggregateWindowsConfig: AggregateWindowsConfig = AggregateWindowsConfig.Default,
      collectingListener: => ResultsCollectingListener[Any] = ResultsCollectingListenerHolder.registerListener
  ): LocalModelData = {
    val sourceComponent = SourceFactory.noParamUnboundedStreamFactory[TestRecord](
      EmitWatermarkAfterEachElementCollectionSource.create[TestRecord](list, _.timestamp, Duration.ofHours(1))
    )
    LocalModelData(
      ConfigFactory.empty(),
      ComponentDefinition("start", sourceComponent) :: FlinkBaseUnboundedComponentProvider.create(
        DocsConfig.Default,
        aggregateWindowsConfig
      ) ::: FlinkBaseComponentProvider.Components,
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener)
    )
  }

  private val processValidator: ProcessValidator = ProcessValidator.default(modelData())

  test("aggregates are properly validated") {
    validateOk("#AGG.approxCardinality", "#input.str", Typed[Long])
    validateOk("#AGG.countWhen", """#input.str == "adf"""", Typed[Long])

    validateOk("#AGG.average", """#input.eId""", Typed[Double])
    validateOk("#AGG.average", """1""", Typed[Double])
    validateOk("#AGG.average", """1.5""", Typed[Double])

    validateOk("#AGG.average", """T(java.math.BigInteger).ONE""", Typed[java.math.BigDecimal])
    validateOk("#AGG.average", """T(java.math.BigDecimal).ONE""", Typed[java.math.BigDecimal])

    validateOk("#AGG.stddevPop", "1", Typed[Double])
    validateOk("#AGG.stddevSamp", "1", Typed[Double])
    validateOk("#AGG.varPop", "1", Typed[Double])
    validateOk("#AGG.varSamp", "1", Typed[Double])

    validateOk("#AGG.stddevPop", "1.5", Typed[Double])
    validateOk("#AGG.stddevSamp", "1.5", Typed[Double])
    validateOk("#AGG.varPop", "1.5", Typed[Double])
    validateOk("#AGG.varSamp", "1.5", Typed[Double])

    validateOk("#AGG.stddevPop", """T(java.math.BigInteger).ONE""", Typed[java.math.BigDecimal])
    validateOk("#AGG.stddevSamp", """T(java.math.BigInteger).ONE""", Typed[java.math.BigDecimal])
    validateOk("#AGG.varPop", """T(java.math.BigInteger).ONE""", Typed[java.math.BigDecimal])
    validateOk("#AGG.varSamp", """T(java.math.BigInteger).ONE""", Typed[java.math.BigDecimal])

    validateOk("#AGG.stddevPop", """T(java.math.BigDecimal).ONE""", Typed[java.math.BigDecimal])
    validateOk("#AGG.stddevSamp", """T(java.math.BigDecimal).ONE""", Typed[java.math.BigDecimal])
    validateOk("#AGG.varPop", """T(java.math.BigDecimal).ONE""", Typed[java.math.BigDecimal])
    validateOk("#AGG.varSamp", """T(java.math.BigDecimal).ONE""", Typed[java.math.BigDecimal])

    validateOk("#AGG.set", "#input.str", Typed.fromDetailedType[java.util.Set[String]])
    validateOk(
      "#AGG.map({f1: #AGG.sum, f2: #AGG.set})",
      "{f1: #input.eId, f2: #input.str}",
      Typed.record(Map("f1" -> Typed[java.lang.Long], "f2" -> Typed.fromDetailedType[java.util.Set[String]]))
    )

    validateError("#AGG.sum", "#input.str", "Invalid aggregate type: String, should be: Number")
    validateError("#AGG.countWhen", "#input.str", "Invalid aggregate type: String, should be: Boolean")
    validateError("#AGG.average", "#input.str", "Invalid aggregate type: String, should be: Number")

    validateError("#AGG.stddevPop", "#input.str", "Invalid aggregate type: String, should be: Number")
    validateError("#AGG.stddevSamp", "#input.str", "Invalid aggregate type: String, should be: Number")
    validateError("#AGG.varPop", "#input.str", "Invalid aggregate type: String, should be: Number")
    validateError("#AGG.varSamp", "#input.str", "Invalid aggregate type: String, should be: Number")

    validateError(
      "#AGG.map({f1: #AGG.set, f2: #AGG.set})",
      "{f1: #input.str}",
      "Fields do not match, aggregateBy: f1, aggregator: f1, f2"
    )
    validateError(
      "#AGG.map({f1: #AGG.max})",
      "{f1: #input.str}",
      "Invalid fields: f1 - Invalid aggregate type: String, should be: Number"
    )
    validateError("#AGG.map({f1: #AGG.max})", "#input.str", "aggregateBy should be declared as fixed map")
  }

  test("sum aggregate") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b")))
    val testProcess = sliding("#AGG.sum", "#input.eId", emitWhenEventLeft = false)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(1, 3, 7)
  }

  test("sum aggregate with zeros") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 0, "a"), TestRecordHours(id, 1, 1, "b"), TestRecordHours(id, 2, 0, "b")))
    val testProcess = sliding("#AGG.sum", "#input.eId", emitWhenEventLeft = false)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(0, 1, 1)
  }

  test("countWhen aggregate") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "c")))
    val testProcess =
      sliding("#AGG.countWhen", """#input.str == "a" || #input.str == "b" """, emitWhenEventLeft = false)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(1, 2, 1)
  }

  test("average aggregate") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b")))
    val testProcess = sliding("#AGG.average", "#input.eId", emitWhenEventLeft = false)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(1.0d, 1.5, 3.5)
  }

  test("standard deviation and average aggregates") {
    val table = Table(
      ("aggregate", "secondValue"),
        ("#AGG.stddevPop", Math.sqrt(0.25)),
      ("#AGG.stddevSamp", Math.sqrt(0.5)),
        ("#AGG.varPop", 0.25),
          ("#AGG.varSamp", 0.5)
    )

    forAll(table) { (aggregationName, secondValue) =>
      val id = "1"

      val model =
        modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b")))
      val testProcess = sliding(aggregationName, "#input.eId", emitWhenEventLeft = false)

      val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
      val mapped = aggregateVariables
        .map(e => e.asInstanceOf[Double])
      mapped.size shouldBe 2
      mapped(0) shouldBe 0.0 +- 0.0001
      mapped(1) shouldBe secondValue +- 0.0001
    }
  }

  test("sliding aggregate should emit context of variables") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b")))
    val testProcess =
      sliding("#AGG.sum", "#input.eId", emitWhenEventLeft = false, afterAggregateExpression = "#input.eId")

    val nodeResults = runCollectOutputVariables(id, model, testProcess)
    nodeResults.map(_.variableTyped[Number]("fooVar").get) shouldBe List(1, 2, 5)
  }

  test("sum aggregate for out of order elements") {
    val id = "1"

    val model = modelData(
      List(
        TestRecordHours(id, 0, 1, "a"),
        TestRecordHours(id, 1, 2, "b"),
        TestRecordHours(id, 2, 5, "b"),
        TestRecordHours(id, 1, 1, "b")
      )
    )
    val testProcess = sliding("#AGG.sum", "#input.eId", emitWhenEventLeft = false)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(1, 3, 7, 4)
  }

  test("emit aggregate when event left the slide") {
    val id = "1"

    val model = modelData(
      List(
        TestRecordHours(id, 0, 1, ""),
        TestRecordHours(id, 1, 2, ""),
        TestRecordHours(id, 2, 5, "")
      )
    )
    val testProcess = sliding("#AGG.sum", "#input.eId", emitWhenEventLeft = true)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(1, 3, 7, 5, 0)
  }

  test("emit aggregate when event left the slide should not emit context") {
    val testProcess =
      sliding("#AGG.sum", "#input.eId", emitWhenEventLeft = true, afterAggregateExpression = "#input.eId")

    val result = processValidator.validate(testProcess, isFragment = false)(jobDataFor(testProcess))

    inside(result.result) {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError("Unresolved reference 'input'", "after-aggregate-expression-", _, _, _),
              _
            )
          ) =>
    }
  }

  test("sum tumbling aggregate") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b")))
    val testProcess = tumbling("#AGG.sum", "#input.eId", emitWhen = TumblingWindowTrigger.OnEnd)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(3, 5)
  }

  test("set tumbling aggregate") {
    val id = "1"
    val model = modelData(
      List(
        TestRecordHours(id, 10, 1, "a"),
        TestRecordHours(id, 11, 2, "b"),
        TestRecordHours(id, 13, 5, "b"),
        TestRecordHours(id, 14, 6, "b"),
      )
    )
    val testProcess = tumbling("#AGG.set", "#input.eId", emitWhen = TumblingWindowTrigger.OnEnd)

    val aggregateVariables = runCollectOutputAggregate[Set[Number]](id, model, testProcess)
    aggregateVariables shouldBe List(Set(1, 2), Set(5), Set(6)).map(_.asJava)
  }

  test("set tumbling aggregate - daily windows in GMT+03 - with aggregate offset set to -3H") {
    List(TumblingWindowTrigger.OnEnd, TumblingWindowTrigger.OnEndWithExtraWindow).foreach { trigger =>
      val id = "1"

      val t0  = OffsetDateTime.parse("2011-12-02T23:59:30+03:00").toEpochSecond * 1000L
      val t1a = OffsetDateTime.parse("2011-12-03T00:00:30+03:00").toEpochSecond * 1000L
      val t1b = OffsetDateTime.parse("2011-12-03T23:59:30+03:00").toEpochSecond * 1000L
      val t2  = OffsetDateTime.parse("2011-12-04T02:59:30+03:00").toEpochSecond * 1000L

      val model = modelData(
        List(
          TestRecordWithTimestamp(id, t0, 1, "a"),
          TestRecordWithTimestamp(id, t1a, 2, "b"),
          TestRecordWithTimestamp(id, t1b, 5, "b"),
          TestRecordWithTimestamp(id, t2, 7, "b"),
        ),
        AggregateWindowsConfig(Some(Duration.parse("PT-3H")))
      )

      val testProcess = tumbling(
        "#AGG.set",
        "#input.eId",
        emitWhen = trigger,
        Map("windowLength" -> "T(java.time.Duration).parse('P1D')")
      )

      val aggregateVariables = runCollectOutputAggregate[java.util.Set[Number]](id, model, testProcess)
      var expected           = List(Set(1), Set(2, 5), Set(7))
      if (trigger == TumblingWindowTrigger.OnEndWithExtraWindow) {
        expected = expected :+ Set()
      }
      aggregateVariables shouldBe expected.map(_.asJava)

    }
  }

  test("sum tumbling aggregate when in fragment") {
    val scenario = ScenarioBuilder
      .streaming("aggregateTest")
      .parallelism(1)
      .stateOnDisk(true)
      .source("start", "start")
      .fragmentOneOut(
        "fragmentWithTumblingAggregate",
        "fragmentWithTumblingAggregate",
        "aggregate",
        "fragmentResult",
        ("aggBy", "#input.eId".spel),
        ("key", "#input.id".spel)
      )
      .buildSimpleVariable("key", "key", "#fragmentResult.key".spel)
      .buildSimpleVariable("globalVarAccessTest", "globalVarAccessTest", "#meta.processName".spel)
      .emptySink("end", "dead-end")

    val resolvedScenario = resolveFragmentWithTumblingAggregate(scenario)

    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b")))

    val aggregateVariables = runCollectOutputAggregate[java.util.Map[String, Any]](id, model, resolvedScenario)
    aggregateVariables.map(_.asScala("aggresult")) shouldBe List(3, 5)
  }

  test("tumbling aggregate in fragment clears context of main scenario") {
    val scenario = ScenarioBuilder
      .streaming("aggregateTest")
      .parallelism(1)
      .stateOnDisk(true)
      .source("start", "start")
      .fragmentOneOut(
        "fragmentWithTumblingAggregate",
        "fragmentWithTumblingAggregate",
        "aggregate",
        "fragmentResult",
        ("aggBy", "#input.eId".spel),
        ("key", "#input.id".spel)
      )
      .buildSimpleVariable("inputVarAccessTest", "inputVarAccessTest", "#input".spel)
      .emptySink("end", "dead-end")

    val resolvedScenario = resolveFragmentWithTumblingAggregate(scenario)

    val id = "1"
    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b")))

    lazy val run = runProcess(model, resolvedScenario)

    the[IllegalArgumentException] thrownBy run should have message "Compilation errors: ExpressionParserCompilationError(Unresolved reference 'input',inputVarAccessTest,Some(ParameterName($expression)),#input,None)"
  }

  test("sum tumbling aggregate emit on event, emit context of variables") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b")))
    val testProcess = tumbling(
      "#AGG.list",
      "#input.eId",
      emitWhen = TumblingWindowTrigger.OnEvent,
      afterAggregateExpression = "#input.eId"
    )

    val nodeResults = runCollectOutputVariables(id, model, testProcess)

    nodeResults.map(_.variableTyped[Number]("fooVar").get) shouldBe List(1, 2, 5)

    val aggregateVariables = nodeResults.map(_.variableTyped[java.util.List[Number]]("fragmentResult").get)
    // TODO: reverse order in aggregate
    aggregateVariables shouldBe List(asList(1), asList(2, 1), asList(5))
  }

  test("sum tumbling aggregate for out of order elements") {
    val id = "1"

    val model = modelData(
      List(
        TestRecordHours(id, 0, 1, "a"),
        TestRecordHours(id, 1, 2, "b"),
        TestRecordHours(id, 2, 5, "b"),
        TestRecordHours(id, 1, 1, "b")
      )
    )
    val testProcess = tumbling("#AGG.sum", "#input.eId", emitWhen = TumblingWindowTrigger.OnEnd)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(4, 5)
  }

  test("drop late events") {
    val id = "1"

    val model = modelData(
      List(
        TestRecordHours(id, 0, 1, "a"),
        TestRecordHours(id, 1, 2, "b"),
        TestRecordHours(id, 3, 5, "b"), // watermark advances more than max out of orderness (1h in test)
        TestRecordHours(id, 1, 1, "b")
      )
    ) // lost because watermark advanced to 2
    val testProcess = tumbling("#AGG.sum", "#input.eId", emitWhen = TumblingWindowTrigger.OnEnd)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(3, 5)
  }

  test("emit aggregate for extra window when no data come") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b")))
    val testProcess = tumbling("#AGG.sum", "#input.eId", emitWhen = TumblingWindowTrigger.OnEndWithExtraWindow)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(3, 5, 0)
  }

  test("emit aggregate for extra window when no data come for average aggregator for return type double") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a")))
    val testProcess = tumbling("#AGG.average", "#input.eId", emitWhen = TumblingWindowTrigger.OnEndWithExtraWindow)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables.length shouldEqual (2)
    aggregateVariables(0) shouldEqual 1.0
    aggregateVariables(1).asInstanceOf[Double].isNaN shouldBe true
  }

  test(
    "emit aggregate for extra window when no data come for standard deviation and variance aggregator for return type double"
  ) {
    for (aggregatorName <- List("#AGG.stddevPop", "#AGG.stddevSamp", "#AGG.varPop", "#AGG.varSamp")) {
      val id = "1"

      val model =
        modelData(List(TestRecordHours(id, 0, 1, "a")))
      val testProcess = tumbling(aggregatorName, "#input.eId", emitWhen = TumblingWindowTrigger.OnEndWithExtraWindow)

      val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
      aggregateVariables.length shouldEqual (2)
      aggregateVariables(0) shouldEqual 0.0
      aggregateVariables(1).asInstanceOf[Double].isNaN shouldBe true
    }
  }

  test("emit aggregate for extra window when no data come for average aggregator for return type BigDecimal") {
    val id = "1"

    val model =
      modelData(List(TestRecordHours(id, 0, 1, "a")))
    val testProcess =
      tumbling("#AGG.average", """T(java.math.BigDecimal).ONE""", emitWhen = TumblingWindowTrigger.OnEndWithExtraWindow)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldEqual List(new java.math.BigDecimal("1"), null)
  }

  test(
    "emit aggregate for extra window when no data come for standard deviation and variance aggregator for return type BigDecimal"
  ) {
    val table = Table(
      "aggregatorExpression",
      "#AGG.stddevPop", "#AGG.stddevSamp", "#AGG.varPop", "#AGG.varSamp"
    )

    forAll(table) { aggregatorName =>
      val id = "1"

      val model =
        modelData(List(TestRecordHours(id, 0, 1, "a")))
      val testProcess =
        tumbling(
          aggregatorName,
          """T(java.math.BigDecimal).ONE""",
          emitWhen = TumblingWindowTrigger.OnEndWithExtraWindow
        )

      val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
      aggregateVariables shouldEqual List(new java.math.BigDecimal("0"), null)
    }
  }

  test("emit aggregate for extra window when no data come - out of order elements") {
    val id = "1"

    val model = modelData(
      List(
        TestRecordHours(id, 0, 1, "a"),
        TestRecordHours(id, 1, 2, "b"),
        TestRecordHours(id, 2, 5, "b"),
        TestRecordHours(id, 1, 1, "b")
      )
    )
    val testProcess = tumbling("#AGG.sum", "#input.eId", emitWhen = TumblingWindowTrigger.OnEndWithExtraWindow)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(4, 5, 0)
  }

  test("sum session aggregate") {
    val id = "1"

    val model = modelData(
      List(
        TestRecordHours(id, 0, 1, "a"),
        TestRecordHours(id, 1, 2, "b"),
        TestRecordHours(id, 2, 3, "d"),
        TestRecordHours(id, 3, 4, "d"),

        // gap
        TestRecordHours(id, 6, 5, "b"),
        TestRecordHours(id, 6, 6, "b"),
        TestRecordHours(id, 6, 7, "stop"),
        // stop condition
        TestRecordHours(id, 6, 8, "a")
      )
    )
    val testProcess = session("#AGG.list", "#input.eId", SessionWindowTrigger.OnEnd, "#input.str == 'stop'")

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(asList(4, 3, 2, 1), asList(7, 6, 5), asList(8))

    val nodeResults = runCollectOutputVariables(id, model, testProcess)
    nodeResults.flatMap(_.variableTyped[TestRecordHours]("input")) shouldBe Nil
  }

  test("sum session aggregate on event with context") {
    val id = "1"

    val testRecords =
      List(
        TestRecordHours(id, 0, 1, "a"),
        TestRecordHours(id, 2, 2, "d"),
        // gap
        TestRecordHours(id, 6, 3, "b"),
        TestRecordHours(id, 6, 4, "stop"),
        // stop condition
        TestRecordHours(id, 6, 5, "a")
      )
    val model       = modelData(testRecords)
    val testProcess = session("#AGG.list", "#input.eId", SessionWindowTrigger.OnEvent, "#input.str == 'stop'")

    val outputVariables = runCollectOutputVariables(id, model, testProcess)
    outputVariables.map(_.variableTyped[java.util.List[Number]]("fragmentResult").get) shouldBe List(
      asList(1),
      asList(2, 1),
      asList(3),
      asList(4, 3),
      asList(5)
    )
    outputVariables.map(_.variableTyped[TestRecordHours]("input").get) shouldBe testRecords
  }

  test("map aggregate") {
    val id = "1"

    val model = modelData(
      List(
        TestRecordHours(id, 1, 1, "a"),
        TestRecordHours(id, 2, 2, "b"),
        TestRecordHours(id, 3, 3, "c"),
        TestRecordHours(id, 3, 4, "d"),
        TestRecordHours("2", 3, 5, "no"),
        TestRecordHours(id, 4, 6, "e"),
        TestRecordHours(id, 5, 7, "a"),
        TestRecordHours(id, 5, 8, "b")
      )
    )
    val testProcess = sliding(
      "#AGG.map({sum: #AGG.sum, first: #AGG.first, last: #AGG.last, set: #AGG.set, hll: #AGG.approxCardinality})",
      "{sum: #input.eId, first: #input.eId, last: #input.eId, set: #input.str, hll: #input.str}",
      emitWhenEventLeft = false
    )

    val aggregateVariables = runCollectOutputAggregate[util.Map[String, Any]](id, model, testProcess).map(_.asScala)

    aggregateVariables shouldBe List(
      Map("first" -> 1, "last" -> 1, "hll" -> 1, "sum" -> 1, "set"  -> Set("a").asJava),
      Map("first" -> 1, "last" -> 2, "hll" -> 2, "sum" -> 3, "set"  -> Set("a", "b").asJava),
      Map("first" -> 2, "last" -> 3, "hll" -> 2, "sum" -> 5, "set"  -> Set("b", "c").asJava),
      Map("first" -> 2, "last" -> 4, "hll" -> 3, "sum" -> 9, "set"  -> Set("b", "c", "d").asJava),
      Map("first" -> 3, "last" -> 6, "hll" -> 3, "sum" -> 13, "set" -> Set("c", "d", "e").asJava),
      Map("first" -> 6, "last" -> 7, "hll" -> 2, "sum" -> 13, "set" -> Set("e", "a").asJava),
      Map("first" -> 6, "last" -> 8, "hll" -> 3, "sum" -> 21, "set" -> Set("e", "a", "b").asJava)
    )
  }

  test("base aggregates test") {
    val id = "1"

    val collectingListener = ResultsCollectingListenerHolder.registerListener
    val model = modelData(
      List(
        TestRecordHours(id, 1, 2, "a"),
        TestRecordHours(id, 2, 1, "b")
      ),
      collectingListener = collectingListener
    )

    val aggregates = List(
      ("sum", 3),
      ("first", 2),
      ("last", 1),
      ("max", 2),
      ("min", 1),
      ("list", util.Arrays.asList(2, 1)),
      ("approxCardinality", 2)
    )

    // "aggregate-sliding", aggregator, aggregateBy, "windowLength", Map("emitWhenEventLeft" -> emitWhenEventLeft.toString
    val testProcess = process(
      aggregates
        .map(_._1)
        .map(name =>
          AggregateData(
            "aggregate-sliding",
            s"#AGG.$name",
            "#input.eId",
            "windowLength",
            Map("emitWhenEventLeft" -> "false"),
            name
          )
        ): _*
    )

    runProcess(model, testProcess)
    val lastResult = variablesForKey(collectingListener, id).last
    aggregates.foreach { case (name, expected) =>
      lastResult.variableTyped[AnyRef](s"fragmentResult$name").get shouldBe expected
    }
  }

  private def runCollectOutputAggregate[T <: AnyRef](
      key: String,
      model: LocalModelData,
      testProcess: CanonicalProcess
  ): List[T] = {
    runCollectOutputVariables(key, model, testProcess).map(_.variableTyped[T]("fragmentResult").get)
  }

  private def runCollectOutputVariables(
      key: String,
      model: LocalModelData,
      testProcess: CanonicalProcess
  ): List[TestProcess.ResultContext[Any]] = {
    runProcess(model, testProcess)
    val collectingListener = model.configCreator.asInstanceOf[ConfigCreatorWithCollectingListener].collectingListener
    variablesForKey(collectingListener, key)
  }

  private def runProcess(
      model: LocalModelData,
      testProcess: CanonicalProcess
  ): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(stoppableEnv, model)(testProcess)
    stoppableEnv.executeAndWaitForFinished(testProcess.name.value)()
  }

  private def variablesForKey(
      collectingListener: ResultsCollectingListener[Any],
      key: String
  ): List[TestProcess.ResultContext[Any]] = {
    collectingListener.results
      .nodeResults("end")
      .filter(_.variableTyped[String](VariableConstants.KeyVariableName).contains(key))
  }

  private def validateError(aggregator: String, aggregateBy: String, error: String): Unit = {
    val result = validateConfig(aggregator, aggregateBy)
    result.result shouldBe Symbol("invalid")
    result.result.swap.toOption.get shouldBe NonEmptyList.of(CannotCreateObjectError(error, "transform"))
  }

  private def validateOk(aggregator: String, aggregateBy: String, typingResult: TypingResult): Unit = {
    val result = validateConfig(aggregator, aggregateBy)
    result.result shouldBe Symbol("valid")
    result.variablesInNodes("end")("fragmentResult") shouldBe typingResult
  }

  private def validateConfig(aggregator: String, aggregateBy: String): CompilationResult[Unit] = {
    val scenario = sliding(aggregator, aggregateBy, emitWhenEventLeft = false)
    processValidator.validate(scenario, isFragment = false)(jobDataFor(scenario))
  }

  private def tumbling(
      aggregator: String,
      aggregateBy: String,
      emitWhen: TumblingWindowTrigger,
      additionalParams: Map[String, String] = Map.empty,
      afterAggregateExpression: String = "null"
  ) = {
    process(
      "aggregate-tumbling",
      aggregator,
      aggregateBy,
      "windowLength",
      Map("emitWhen" -> enumToExpr(emitWhen)) ++ additionalParams,
      afterAggregateExpression
    )
  }

  private def sliding(
      aggregator: String,
      aggregateBy: String,
      emitWhenEventLeft: Boolean,
      afterAggregateExpression: String = "null"
  ) = {
    process(
      "aggregate-sliding",
      aggregator,
      aggregateBy,
      "windowLength",
      Map("emitWhenEventLeft" -> emitWhenEventLeft.toString),
      afterAggregateExpression
    )
  }

  private def session(
      aggregator: String,
      aggregateBy: String,
      emitWhen: SessionWindowTrigger,
      endSessionCondition: String,
      afterAggregateExpression: String = "null"
  ) = {
    process(
      "aggregate-session",
      aggregator,
      aggregateBy,
      "sessionTimeout",
      Map("endSessionCondition" -> endSessionCondition, "emitWhen" -> enumToExpr(emitWhen)),
      afterAggregateExpression
    )
  }

  private def enumToExpr[T <: Enum[T]](enumValue: T): String = {
    ParameterTypeEditorDeterminer.extractEnumValue(enumValue.getClass.asInstanceOf[Class[T]])(enumValue).expression
  }

  private def process(
      aggregatingNode: String,
      aggregator: String,
      aggregateBy: String,
      timeoutParamName: String,
      additionalParams: Map[String, String],
      afterAggregateExpression: String
  ): CanonicalProcess = {
    process(
      AggregateData(
        aggregatingNode,
        aggregator,
        aggregateBy,
        timeoutParamName,
        additionalParams,
        afterAggregateExpression = afterAggregateExpression
      )
    )
  }

  private def process(aggregateData: AggregateData*): CanonicalProcess = {

    def params(data: AggregateData) = {
      val baseParams: List[(String, Expression)] = List(
        "groupBy"             -> "#id".spel,
        "aggregateBy"         -> data.aggregateBy.spel,
        "aggregator"          -> data.aggregator.spel,
        data.timeoutParamName -> "T(java.time.Duration).parse('PT2H')".spel
      )
      baseParams ++ data.additionalParams.mapValuesNow(_.spel).toList
    }

    val beforeAggregate = ScenarioBuilder
      .streaming("aggregateTest")
      .parallelism(1)
      .stateOnDisk(true)
      .source("start", "start")
      .buildSimpleVariable("id", "id", "#input.id".spel)

    aggregateData
      .foldLeft(beforeAggregate) { case (builder, definition) =>
        builder
          .customNode(
            s"transform${definition.idSuffix}",
            s"fragmentResult${definition.idSuffix}",
            definition.aggregatingNode,
            params(definition): _*
          )
          .buildSimpleVariable(
            s"after-aggregate-expression-${definition.idSuffix}",
            s"fooVar${definition.idSuffix}",
            definition.afterAggregateExpression.spel
          )
      }
      .emptySink("end", "dead-end")
  }

  private def resolveFragmentWithTumblingAggregate(scenario: CanonicalProcess): CanonicalProcess = {
    val fragmentWithTumblingAggregate = CanonicalProcess(
      MetaData("fragmentWithTumblingAggregate", FragmentSpecificData()),
      List(
        canonicalnode.FlatNode(
          FragmentInputDefinition(
            "start",
            List(
              FragmentParameter(ParameterName("aggBy"), FragmentClazzRef[Int]),
              FragmentParameter(ParameterName("key"), FragmentClazzRef[String])
            )
          )
        ),
        canonicalnode.FlatNode(
          CustomNode(
            "agg",
            Some("aggresult"),
            "aggregate-tumbling",
            List(
              NodeParameter(ParameterName("groupBy"), "#key".spel),
              NodeParameter(ParameterName("aggregator"), "#AGG.sum".spel),
              NodeParameter(ParameterName("aggregateBy"), "#aggBy".spel),
              NodeParameter(ParameterName("windowLength"), "T(java.time.Duration).parse('PT2H')".spel),
              NodeParameter(
                ParameterName("emitWhen"),
                "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.TumblingWindowTrigger).OnEnd".spel
              )
            )
          )
        ),
        canonicalnode.FlatNode(
          FragmentOutputDefinition(
            "out1",
            "aggregate",
            List(Field("key", "#key".spel), Field("aggresult", "#aggresult".spel))
          )
        )
      ),
      List.empty
    )

    FragmentResolver(List(fragmentWithTumblingAggregate)).resolve(scenario).toOption.get
  }

  private def jobDataFor(scenario: CanonicalProcess) = {
    JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
  }

}

case class AggregateData(
    aggregatingNode: String,
    aggregator: String,
    aggregateBy: String,
    timeoutParamName: String,
    additionalParams: Map[String, String],
    idSuffix: String = "",
    afterAggregateExpression: String = "null"
)

trait TestRecord {
  val id: String
  val eId: Int
  val str: String

  def timestamp: Long
}

case class TestRecordHours(id: String, timeHours: Int, eId: Int, str: String) extends TestRecord {
  override def timestamp: Long = timeHours * 3600L * 1000
}

case class TestRecordWithTimestamp(id: String, timestamp: Long, eId: Int, str: String) extends TestRecord

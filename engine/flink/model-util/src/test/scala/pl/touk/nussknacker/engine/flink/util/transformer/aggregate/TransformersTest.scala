package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.time.Duration
import java.util
import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.compile.{CompilationResult, ProcessValidator}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory.NoParamSourceFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.collection.JavaConverters._

class TransformersTest extends FunSuite with FlinkSpec with Matchers {

  def modelData(list: List[TestRecord] = List()): LocalModelData = LocalModelData(ConfigFactory.empty(), new Creator(list))

  val validator: ProcessValidator = modelData().validator

  test("aggregates are properly validated") {
    validateOk("#AGG.approxCardinality","#input.str",  Typed[Long])
    validateOk("#AGG.set","#input.str",  Typed.fromDetailedType[java.util.Set[String]])
    validateOk("#AGG.map({f1: #AGG.sum, f2: #AGG.set})",
      "{f1: #input.eId, f2: #input.str}",
      TypedObjectTypingResult(Map("f1" -> Typed[java.lang.Long], "f2" -> Typed.fromDetailedType[java.util.Set[String]])))

    validateError("#AGG.sum","#input.str", "Invalid aggregate type: String, should be: Number")
    validateError("#AGG.map({f1: #AGG.set, f2: #AGG.set})","{f1: #input.str}", "Fields do not match, aggregateBy: f1, aggregator: f1, f2")
    validateError("#AGG.map({f1: #AGG.max})","{f1: #input.str}", "Invalid fields: f1 - Invalid aggregate type: String, should be: Number")
    validateError("#AGG.map({f1: #AGG.max})","#input.str", "aggregateBy should be declared as fixed map")
  }

  test("sum aggregate") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b")))
    val testProcess = process("aggregate-sliding", s"T(${classOf[AggregateHelper].getName}).SUM",
      "#input.eId", emitWhenEventLeft = Some(false), None)

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = endAggregateVariable[Number](collectingListener, id)

    aggregateVariables shouldBe List(1, 3, 7)
  }

  test("sum aggregate with zeros") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 0, "a"),
      TestRecord(id, 1, 1, "b"),
      TestRecord(id, 2, 0, "b")))
    val testProcess = process("aggregate-sliding", s"T(${classOf[AggregateHelper].getName}).SUM",
      "#input.eId", emitWhenEventLeft = Some(false), None)

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = endAggregateVariable[Number](collectingListener, id)

    aggregateVariables shouldBe List(0, 1, 1)
  }


  test("sum aggregate for out of order elements") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b"),
      TestRecord(id, 1, 1, "b")))
    val testProcess = process("aggregate-sliding", s"T(${classOf[AggregateHelper].getName}).SUM",
      "#input.eId", Some(false), None)

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = endAggregateVariable[Number](collectingListener, id)

    aggregateVariables shouldBe List(1, 3, 7, 4)
  }

  test("emit aggregate when event left the slide") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, ""),
      TestRecord(id, 1, 2, ""),
      TestRecord(id, 2, 5, "")
    ))
    val testProcess = process("aggregate-sliding", s"T(${classOf[AggregateHelper].getName}).SUM",
      "#input.eId", emitWhenEventLeft = Some(true), None)

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = endAggregateVariable[Number](collectingListener, id)

    aggregateVariables shouldBe List(1, 3, 7, 5, 0)
  }

  test("sum tumbling aggregate") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b")))
    val testProcess = process("aggregate-tumbling", s"T(${classOf[AggregateHelper].getName}).SUM",
      "#input.eId", emitWhenEventLeft = None, emitExtraWindowWhenNoData = Some(false))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = endAggregateVariable[Number](collectingListener, id)

    aggregateVariables shouldBe List(3, 5)
  }

  test("sum tumbling aggregate for out of order elements") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b"),
      TestRecord(id, 1, 1, "b")))
    val testProcess = process("aggregate-tumbling", s"T(${classOf[AggregateHelper].getName}).SUM", "#input.eId", None, Some(false))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = endAggregateVariable[Number](collectingListener, id)

    aggregateVariables shouldBe List(4, 5)
  }

  test("emit aggregate for extra window when no data come") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b")))
    val testProcess = process("aggregate-tumbling", s"T(${classOf[AggregateHelper].getName}).SUM",
      "#input.eId", emitWhenEventLeft = None, emitExtraWindowWhenNoData = Some(true))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = endAggregateVariable[Number](collectingListener, id)

    aggregateVariables shouldBe List(3, 5, 0)
  }

  test("emit aggregate for extra window when no data come - out of order elements") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b"),
      TestRecord(id, 1, 1, "b")
    ))
    val testProcess = process("aggregate-tumbling", s"T(${classOf[AggregateHelper].getName}).SUM",
      "#input.eId", emitWhenEventLeft = None, emitExtraWindowWhenNoData = Some(true))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = endAggregateVariable[Number](collectingListener, id)

    aggregateVariables shouldBe List(4, 5, 0)
  }

  test("map aggregate") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 1, 1, "a"),
      TestRecord(id, 2, 2, "b"),
      TestRecord(id, 3, 3, "c"),
      TestRecord(id, 3, 4, "d"),
      TestRecord("2", 3, 5, "no"),
      TestRecord(id, 4, 6, "e"),
      TestRecord(id, 5, 7, "a"),
      TestRecord(id, 5, 8, "b")

    ))
    val testProcess = process("aggregate-sliding", "#AGG.map({sum: #AGG.sum, first: #AGG.first, last: #AGG.last, set: #AGG.set, hll: #AGG.approxCardinality})",
      "{sum: #input.eId, first: #input.eId, last: #input.eId, set: #input.str, hll: #input.str}", emitWhenEventLeft = Some(false), None)

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = endAggregateVariable[util.Map[String, Any]](collectingListener, id).map(_.asScala)

    aggregateVariables shouldBe List(
      Map("first" -> 1, "last" -> 1, "hll" -> 1, "sum" -> 1, "set" -> Set("a").asJava),
      Map("first" -> 1, "last" -> 2, "hll" -> 2, "sum" -> 3, "set" -> Set("a", "b").asJava),
      Map("first" -> 2, "last" -> 3, "hll" -> 2, "sum" -> 5, "set" -> Set("b", "c").asJava),
      Map("first" -> 2, "last" -> 4, "hll" -> 3, "sum" -> 9, "set" -> Set("b", "c", "d").asJava),
      Map("first" -> 3, "last" -> 6, "hll" -> 3, "sum" -> 13, "set" -> Set("c", "d", "e").asJava),
      Map("first" -> 6, "last" -> 7, "hll" -> 2, "sum" -> 13, "set" -> Set("e", "a").asJava),
      Map("first" -> 6, "last" -> 8, "hll" -> 3, "sum" -> 21, "set" -> Set("e", "a", "b").asJava)
    )
  }

  private def runProcess(model: LocalModelData, testProcess: EspProcess, collectingListener: ResultsCollectingListener): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model) {
      override protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
        List(collectingListener) ++ super.listeners(processObjectDependencies)
    }, model.processConfig, ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, DeploymentData.empty, Some(collectingListener.runId))
    stoppableEnv.executeAndWaitForFinished(testProcess.id)()
  }

  private def endAggregateVariable[T](collectingListener: ResultsCollectingListener, key: String) = {
    collectingListener.results[Any].nodeResults("end")
      .filter(_.variableTyped("id").contains(key))
      .map(_.variableTyped[T]("aggregate").get)
  }

  private def validateError(aggregator: String,
                            aggregateBy: String, error: String): Unit = {
    val result = validateConfig(aggregator, aggregateBy)
    result.result shouldBe 'invalid
    result.result.swap.toOption.get shouldBe NonEmptyList.of(CannotCreateObjectError(error, "transform"))
  }

  private def validateOk(aggregator: String,
                         aggregateBy: String, typingResult: TypingResult): Unit = {
    val result = validateConfig(aggregator, aggregateBy)
    result.result shouldBe 'valid
    result.variablesInNodes("end")("aggregate") shouldBe typingResult
  }

  private def validateConfig(aggregator: String, aggregateBy: String): CompilationResult[Unit] = {
    validator.validate(process("aggregate-sliding", aggregator, aggregateBy, emitWhenEventLeft = Some(false), emitExtraWindowWhenNoData = None))
  }

  private def process(aggregatingNode: String, aggregator: String, aggregateBy: String, emitWhenEventLeft: Option[Boolean], emitExtraWindowWhenNoData: Option[Boolean]) = {
    val baseParams: List[(String, Expression)] = List(
      "keyBy" -> "#id",
      "aggregateBy" -> aggregateBy,
      "aggregator" -> aggregator,
      "windowLength" -> "T(java.time.Duration).parse('PT2H')")
    val params = baseParams ++
      emitWhenEventLeft.map(f => "emitWhenEventLeft" -> asSpelExpression(f.toString)) ++
      emitExtraWindowWhenNoData.map(f => "emitExtraWindowWhenNoData" -> asSpelExpression(f.toString))

    EspProcessBuilder
      .id("aggregateTest")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "start")
      .buildSimpleVariable("id", "id", "#input.id")
      .customNode("transform", "aggregate", aggregatingNode, params: _*)
      .emptySink("end", "end")
  }

}

class Creator(input: List[TestRecord]) extends EmptyProcessConfigCreator {

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
    Map(
      "aggregate-sliding" -> WithCategories(SlidingAggregateTransformerV2),
      "aggregate-tumbling" -> WithCategories(TumblingAggregateTransformer))

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] =
    Map("start" -> WithCategories(NoParamSourceFactory(EmitWatermarkAfterEachElementCollectionSource
      .create[TestRecord](input, _.timestamp, Duration.ofHours(1)))))

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
    Map("end" -> WithCategories(SinkFactory.noParam(EmptySink)))

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig
    = super.expressionConfig(processObjectDependencies).copy(globalProcessVariables = Map("AGG"-> WithCategories(new AggregateHelper)))

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory
    = ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))
}

case class TestRecord(id: String, timeHours: Int, eId: Long, str: String) {
  def timestamp: Long = timeHours * 3600L * 1000
}

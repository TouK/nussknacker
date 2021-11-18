package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.streaming.api.scala._
import org.scalatest.{FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, ExpressionParseError}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, TestProcess}
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, ProcessVersion, VariableConstants}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.compile.{CompilationResult, ProcessValidator}
import pl.touk.nussknacker.engine.definition.parameter.editor.ParameterTypeEditorDeterminer
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.exception.ConfigurableExceptionHandlerFactory
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SessionWindowAggregateTransformer, SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.time.Duration
import java.util
import java.util.Arrays.asList
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap

class TransformersTest extends FunSuite with FlinkSpec with Matchers with Inside {

  def modelData(list: List[TestRecord] = List()): LocalModelData = LocalModelData(ConfigFactory
    .empty().withValue("useTypingResultTypeInformation", fromAnyRef(true)), new Creator(list))

  val validator: ProcessValidator = modelData().validator

  test("aggregates are properly validated") {
    validateOk("#AGG.approxCardinality","#input.str",  Typed[Long])
    validateOk("#AGG.set","#input.str",  Typed.fromDetailedType[java.util.Set[String]])
    validateOk("#AGG.map({f1: #AGG.sum, f2: #AGG.set})",
      "{f1: #input.eId, f2: #input.str}",
      TypedObjectTypingResult(ListMap("f1" -> Typed[java.lang.Long], "f2" -> Typed.fromDetailedType[java.util.Set[String]])))

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
    val testProcess = sliding("#AGG.sum",
      "#input.eId", emitWhenEventLeft = false)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(1, 3, 7)
  }

  test("sum aggregate with zeros") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 0, "a"),
      TestRecord(id, 1, 1, "b"),
      TestRecord(id, 2, 0, "b")))
    val testProcess = sliding("#AGG.sum",
      "#input.eId", emitWhenEventLeft = false)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(0, 1, 1)
  }

  test("sliding aggregate should emit context of variables") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b")))
    val testProcess = sliding("#AGG.sum",
      "#input.eId", emitWhenEventLeft = false, afterAggregateExpression = "#input.eId")

    val nodeResults = runCollectOutputVariables(id, model, testProcess)
    nodeResults.map(_.variableTyped[Number]("fooVar").get) shouldBe List(1, 2, 5)
  }

  test("sum aggregate for out of order elements") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b"),
      TestRecord(id, 1, 1, "b")))
    val testProcess = sliding("#AGG.sum",
      "#input.eId", emitWhenEventLeft = false)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(1, 3, 7, 4)
  }

  test("emit aggregate when event left the slide") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, ""),
      TestRecord(id, 1, 2, ""),
      TestRecord(id, 2, 5, "")
    ))
    val testProcess = sliding("#AGG.sum",
      "#input.eId", emitWhenEventLeft = true)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(1, 3, 7, 5, 0)
  }

  test("emit aggregate when event left the slide should not emit context") {
    val testProcess = sliding("#AGG.sum",
      "#input.eId", emitWhenEventLeft = true, afterAggregateExpression = "#input.eId")

    val result = validator.validate(testProcess)

    inside(result.result) {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference 'input'", "after-aggregate-expression-", _, _), Nil)) =>
    }
  }

  test("sum tumbling aggregate") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b")))
    val testProcess = tumbling("#AGG.sum",
      "#input.eId", emitWhen = TumblingWindowTrigger.OnEnd)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(3, 5)
  }

  test("sum tumbling aggregate emit on event") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b")))
    val testProcess = tumbling("#AGG.list",
      "#input.eId", emitWhen = TumblingWindowTrigger.OnEvent)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    //TODO: reverse order in aggregate
    aggregateVariables shouldBe List(asList(1), asList(2, 1), asList(5))
  }

  test("sum tumbling aggregate for out of order elements") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b"),
      TestRecord(id, 1, 1, "b")))
    val testProcess = tumbling("#AGG.sum", "#input.eId", emitWhen = TumblingWindowTrigger.OnEnd)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(4, 5)
  }

  test("emit aggregate for extra window when no data come") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 5, "b")))
    val testProcess = tumbling("#AGG.sum",
      "#input.eId", emitWhen = TumblingWindowTrigger.OnEndWithExtraWindow)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
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
    val testProcess = tumbling("#AGG.sum",
      "#input.eId", emitWhen = TumblingWindowTrigger.OnEndWithExtraWindow)

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(4, 5, 0)
  }

  test("sum session aggregate") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 1, 2, "b"),
      TestRecord(id, 2, 3, "d"),
      TestRecord(id, 3, 4, "d"),

      //gap
      TestRecord(id, 6, 5, "b"),
      TestRecord(id, 6, 6, "b"),
      TestRecord(id, 6, 7, "stop"),
      //stop condition
      TestRecord(id, 6, 8, "a")
    ))
    val testProcess = session("#AGG.list",
      "#input.eId",  SessionWindowTrigger.OnEnd, "#input.str == 'stop'")

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(asList(4, 3, 2, 1), asList(7, 6, 5), asList(8))
  }

  test("sum session aggregate on event") {
    val id = "1"

    val model = modelData(List(
      TestRecord(id, 0, 1, "a"),
      TestRecord(id, 2, 2, "d"),
      //gap
      TestRecord(id, 6, 3, "b"),
      TestRecord(id, 6, 4, "stop"),
      //stop condition
      TestRecord(id, 6, 5, "a")
    ))
    val testProcess = session("#AGG.list",
      "#input.eId", SessionWindowTrigger.OnEvent, "#input.str == 'stop'")

    val aggregateVariables = runCollectOutputAggregate[Number](id, model, testProcess)
    aggregateVariables shouldBe List(asList(1), asList(2, 1), asList(3), asList(4, 3), asList(5))
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
    val testProcess = sliding("#AGG.map({sum: #AGG.sum, first: #AGG.first, last: #AGG.last, set: #AGG.set, hll: #AGG.approxCardinality})",
      "{sum: #input.eId, first: #input.eId, last: #input.eId, set: #input.str, hll: #input.str}", emitWhenEventLeft = false)

    val aggregateVariables = runCollectOutputAggregate[util.Map[String, Any]](id, model, testProcess).map(_.asScala)

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

  test("base aggregates test") {

    val id = "1"

    val model = modelData(List(
      TestRecord(id, 1, 2, "a"),
      TestRecord(id, 2, 1, "b")
    ))

    val aggregates = List(
      ("sum", 3),
      ("first", 2),
      ("last", 1),
      ("max", 2),
      ("min", 1),
      ("list", util.Arrays.asList(2, 1)),
      ("approxCardinality", 2)
    )

    //"aggregate-sliding", aggregator, aggregateBy, "windowLength", Map("emitWhenEventLeft" -> emitWhenEventLeft.toString
    val testProcess = process(aggregates.map(_._1).map(name =>
      AggregateData("aggregate-sliding", s"#AGG.$name", "#input.eId", "windowLength", Map("emitWhenEventLeft" -> "false"), name)): _*)

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    runProcess(model, testProcess, collectingListener)
    val lastResult = variablesForKey(collectingListener, id).last
    aggregates.foreach { case (name, expected) =>
      lastResult.variableTyped[AnyRef](s"aggregate$name").get shouldBe expected
    }
  }

  private def runCollectOutputAggregate[T](key: String, model: LocalModelData, testProcess: EspProcess): List[T] = {
    runCollectOutputVariables(key, model, testProcess).map(_.variableTyped[T]("aggregate").get)
  }

  private def runCollectOutputVariables(key: String, model: LocalModelData, testProcess: EspProcess): List[TestProcess.NodeResult[Any]] = {
    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    runProcess(model, testProcess, collectingListener)
    variablesForKey(collectingListener, key)
  }

  private def runProcess(model: LocalModelData, testProcess: EspProcess, collectingListener: ResultsCollectingListener): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model) {
      override protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
        List(collectingListener) ++ super.listeners(processObjectDependencies)
    }, ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, DeploymentData.empty)
    stoppableEnv.executeAndWaitForFinished(testProcess.id)()
  }

  private def variablesForKey(collectingListener: ResultsCollectingListener, key: String): List[TestProcess.NodeResult[Any]] = {
    collectingListener.results[Any].nodeResults("end")
      .filter(_.variableTyped(VariableConstants.KeyVariableName).contains(key))
  }

  private def validateError(aggregator: String,
                            aggregateBy: String, error: String): Unit = {
    val result = validateConfig(aggregator, aggregateBy)
    result.result shouldBe 'invalid
    result.result.swap.toOption.get shouldBe NonEmptyList.of(CannotCreateObjectError(error, "transform"))
  }

  private def validateOk(aggregator: String,
                         aggregateBy: String,
                         typingResult: TypingResult): Unit = {
    val result = validateConfig(aggregator, aggregateBy)
    result.result shouldBe 'valid
    result.variablesInNodes("end")("aggregate") shouldBe typingResult
  }

  private def validateConfig(aggregator: String, aggregateBy: String): CompilationResult[Unit] = {
    validator.validate(sliding(aggregator, aggregateBy, emitWhenEventLeft = false))
  }

  private def tumbling(aggregator: String, aggregateBy: String, emitWhen: TumblingWindowTrigger, afterAggregateExpression: String = "") = {
    process("aggregate-tumbling", aggregator, aggregateBy, "windowLength", Map("emitWhen" -> enumToExpr(emitWhen)), afterAggregateExpression)
  }

  private def sliding(aggregator: String, aggregateBy: String, emitWhenEventLeft: Boolean, afterAggregateExpression: String = "") = {
    process("aggregate-sliding", aggregator, aggregateBy, "windowLength", Map("emitWhenEventLeft" -> emitWhenEventLeft.toString), afterAggregateExpression)
  }

  private def session(aggregator: String, aggregateBy: String, emitWhen: SessionWindowTrigger, endSessionCondition: String, afterAggregateExpression: String = "") = {
    process("aggregate-session", aggregator, aggregateBy, "sessionTimeout", Map("endSessionCondition" -> endSessionCondition, "emitWhen" -> enumToExpr(emitWhen)), afterAggregateExpression)
  }

  private def enumToExpr[T<:Enum[T]](enum: T): String = {
    ParameterTypeEditorDeterminer.extractEnumValue(`enum`.getClass.asInstanceOf[Class[T]])(enum).expression
  }

  private def process(aggregatingNode: String,
                      aggregator: String,
                      aggregateBy: String,
                      timeoutParamName: String,
                      additionalParams: Map[String, String],
                      afterAggregateExpression: String): EspProcess = {
    process(AggregateData(aggregatingNode, aggregator, aggregateBy, timeoutParamName, additionalParams, afterAggregateExpression = afterAggregateExpression))
  }

  private def process(aggregateData: AggregateData*): EspProcess = {

    def params(data: AggregateData) = {
    val baseParams: List[(String, Expression)] = List(
      "groupBy" -> "#id",
      "aggregateBy" -> data.aggregateBy,
      "aggregator" -> data.aggregator,
      data.timeoutParamName -> "T(java.time.Duration).parse('PT2H')")
      baseParams ++ data.additionalParams.mapValues(asSpelExpression).toList
    }
    val beforeAggregate = EspProcessBuilder
      .id("aggregateTest")
      .parallelism(1)
      .stateOnDisk(true)
      .source("start", "start")
      .buildSimpleVariable("id", "id", "#input.id")

    aggregateData.foldLeft(beforeAggregate) {
      case (builder, definition) =>
        builder
          .customNode(s"transform${definition.idSuffix}", s"aggregate${definition.idSuffix}", definition.aggregatingNode, params(definition): _*)
          .buildSimpleVariable(s"after-aggregate-expression-${definition.idSuffix}", s"fooVar${definition.idSuffix}", definition.afterAggregateExpression)
    }.emptySink("end", "end")
  }

}

class Creator(input: List[TestRecord]) extends EmptyProcessConfigCreator {

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
    Map(
      "aggregate-sliding" -> WithCategories(SlidingAggregateTransformerV2),
      "aggregate-session" -> WithCategories(SessionWindowAggregateTransformer),
      "aggregate-tumbling" -> WithCategories(TumblingAggregateTransformer))

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
    Map("start" -> WithCategories(SourceFactory.noParam[TestRecord](EmitWatermarkAfterEachElementCollectionSource
      .create[TestRecord](input, _.timestamp, Duration.ofHours(1)))))

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
    Map("end" -> WithCategories(SinkFactory.noParam(EmptySink)))

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig
    = super.expressionConfig(processObjectDependencies).copy(globalProcessVariables = Map("AGG"-> WithCategories(new AggregateHelper)))

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory
    = ConfigurableExceptionHandlerFactory(processObjectDependencies)
}

case class AggregateData(aggregatingNode: String,
                         aggregator: String,
                         aggregateBy: String,
                         timeoutParamName: String,
                         additionalParams: Map[String, String],
                         idSuffix: String = "",
                         afterAggregateExpression: String = "")

case class TestRecord(id: String, timeHours: Int, eId: Int, str: String) {
  def timestamp: Long = timeHours * 3600L * 1000
}

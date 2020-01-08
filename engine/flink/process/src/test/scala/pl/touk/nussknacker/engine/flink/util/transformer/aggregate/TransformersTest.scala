package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.util

import cats.data.NonEmptyList
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.scala._
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.test.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.compile.{CompilationResult, ProcessValidator}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory.NoParamSourceFactory
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.aggregates.AggregateHelper
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.SlidingAggregateTransformer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}

import scala.collection.JavaConverters._

//TODO: move TransformersTest to some util module...
class TransformersTest extends FunSuite with Matchers {

  def modelData(list: List[TestRecord] = List()) = LocalModelData(ConfigFactory.empty(), new Creator(list))

  val validator: ProcessValidator = modelData().validator

  test("aggregates are properly validated") {
    validateOk("#AGG.approxCardinality","#input.str",  Typed[Long])
    validateOk("#AGG.set","#input.str",  Typed.fromDetailedType[java.util.Set[String]])
    validateOk("#AGG.map({f1: #AGG.sum, f2: #AGG.set})",
      "{f1: #input.eId, f2: #input.str}",
      TypedObjectTypingResult(Map("f1" -> Typed[Number], "f2" -> Typed.fromDetailedType[java.util.Set[String]])))

    validateError("#AGG.sum","#input.str", "Invalid aggregate type: java.lang.String, should be: java.lang.Number")
    validateError("#AGG.map({f1: #AGG.set, f2: #AGG.set})","{f1: #input.str}", "Fields do not match, aggregateBy: f1, aggregator: f1, f2")
    validateError("#AGG.map({f1: #AGG.max})","{f1: #input.str}", "Invalid fields: f1 - Invalid aggregate type: java.lang.String, should be: java.lang.Number")
    validateError("#AGG.map({f1: #AGG.max})","#input.str", "aggregateBy should be declared as fixed map")
  }

  test("map aggregate") {

    val model = modelData(List(
      TestRecord("1", 1, 1, "a"),
      TestRecord("1", 2, 2, "b"),
      TestRecord("1", 3, 3, "c"),
      TestRecord("1", 3, 4, "d"),
      TestRecord("2", 3, 5, "no"),
      TestRecord("1", 4, 6, "e"),
      TestRecord("1", 5, 7, "a"),
      TestRecord("1", 5, 8, "b")

    ))
    val testProcess = process("#AGG.map({sum: #AGG.sum, first: #AGG.first, last: #AGG.last, set: #AGG.set, hll: #AGG.approxCardinality})",
      "{sum: #input.eId, first: #input.eId, last: #input.eId, set: #input.str, hll: #input.str}")

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    runProcess(model, testProcess, collectingListener)

    val aggregateVariables = collectingListener.results[Any].nodeResults("end")
      .map(_.context.variables).filter(_.get("id").contains("1")).map(_("aggregate").asInstanceOf[util.Map[String, Any]].asScala)

    //below we check that aggregates are computed correctly in 2h time window
    aggregateVariables shouldBe List(
      Map("first" -> 1, "last" -> 1, "hll" -> 1, "sum" -> 1D, "set" -> Set("a").asJava),
      Map("first" -> 1, "last" -> 2, "hll" -> 2, "sum" -> 3D, "set" -> Set("a", "b").asJava),
      Map("first" -> 2, "last" -> 3, "hll" -> 2, "sum" -> 5D, "set" -> Set("b", "c").asJava),
      Map("first" -> 2, "last" -> 4, "hll" -> 3, "sum" -> 9D, "set" -> Set("b", "c", "d").asJava),
      Map("first" -> 3, "last" -> 6, "hll" -> 3, "sum" -> 13D, "set" -> Set("c", "d", "e").asJava),
      Map("first" -> 6, "last" -> 7, "hll" -> 2, "sum" -> 13D, "set" -> Set("e", "a").asJava),
      Map("first" -> 6, "last" -> 8, "hll" -> 3, "sum" -> 21D, "set" -> Set("e", "a", "b").asJava)
    )
  }


  private def runProcess(model: LocalModelData, testProcess: EspProcess, collectingListener: ResultsCollectingListener): Unit = {
    val stoppableEnv = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())
    try {
      val registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(model) {
        override protected def listeners(config: Config): Seq[ProcessListener] = List(collectingListener) ++ super.listeners(config)
      }, model.processConfig)
      registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, Some(collectingListener.runId))
      val id = stoppableEnv.execute(testProcess.id)
      stoppableEnv.waitForJobState(id.getJobID, testProcess.id, ExecutionState.FINISHED)()
    } finally {
      stoppableEnv.stop()
    }
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

  private def validateConfig(aggregator: String,
                             aggregateBy: String): CompilationResult[Unit] = {
    validator.validate(process(aggregator, aggregateBy))
  }

  private def process(aggregator: String, aggregateBy: String) = {
    EspProcessBuilder
      .id("aggregateTest")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "start")
      .buildSimpleVariable("id", "id", "#input.id")
      .customNode("transform", "aggregate", "aggregate",
        "keyBy" -> "#id",
        "aggregateBy" -> aggregateBy,
        "aggregator" -> aggregator,
        "windowLengthInSeconds" -> "3600"
      )
      .emptySink("end", "end")
  }

}

class Creator(input: List[TestRecord]) extends EmptyProcessConfigCreator {

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] =
    Map("aggregate" -> WithCategories(SlidingAggregateTransformer))

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] =
    Map("start" -> WithCategories(NoParamSourceFactory(new CollectionSource[TestRecord](new ExecutionConfig,
      input, Some(TestRecord.timestampExtractor), Typed[TestRecord]))))

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] =
    Map("end" -> WithCategories(SinkFactory.noParam(EmptySink)))

  override def expressionConfig(config: Config): ExpressionConfig
    = super.expressionConfig(config).copy(globalProcessVariables = Map("AGG"-> WithCategories(AggregateHelper)))

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory
    = ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))
}

object TestRecord {
  val timestampExtractor: AscendingTimestampExtractor[TestRecord] = new AscendingTimestampExtractor[TestRecord] {
    override def extractAscendingTimestamp(element: TestRecord): Long = element.timeHours * 3600 * 1000
  }
}

case class TestRecord(id: String, timeHours: Int, eId: Long, str: String)

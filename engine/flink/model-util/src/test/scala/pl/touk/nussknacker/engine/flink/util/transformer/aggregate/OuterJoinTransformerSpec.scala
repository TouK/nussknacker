package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.functions.AssignerWithPunctuatedWatermarks
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestResults
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.test.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory.NoParamSourceFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.function.CoProcessFunctionInterceptor
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrdernessPunctuatedExtractor
import pl.touk.nussknacker.engine.flink.util.transformer.outer.{BranchType, OuterJoinTransformer}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkStreamingProcessRegistrar}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.concurrent.duration.FiniteDuration

class OuterJoinTransformerSpec extends FunSuite with FlinkSpec with Matchers {

  import OuterJoinTransformerSpec._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val MainBranchId = "main"

  private val JoinedBranchId = "joined"

  private val JoinNodeId = "joined-node-id"

  private val EndNodeId = "end-node-id"

  private val KeyVariableName = "keyVar"

  private val OutVariableName = "outVar"

  // Synchronization below not working - should be used other source type or rewritten into TwoInputStreamOperatorTestHarness
  ignore("join aggregate into main stream") {
    val process =  EspProcess(MetaData("sample-join-last", StreamMetaData()), ExceptionHandlerRef(List.empty), NonEmptyList.of[SourceNode](
      GraphBuilder.source("source", "start-main")
        .buildSimpleVariable("build-key", KeyVariableName, "#input.key")
        .branchEnd(MainBranchId, JoinNodeId),
      GraphBuilder.source("joined-source", "start-joined")
        .branchEnd(JoinedBranchId, JoinNodeId),
      GraphBuilder
        .branch(JoinNodeId, "outer-join", Some(OutVariableName),
          List(
            MainBranchId -> List(
              "branchType" -> s"T(${classOf[BranchType].getName}).MAIN",
              "key" -> s"#$KeyVariableName"
            ),
            JoinedBranchId -> List(
              "branchType" -> s"T(${classOf[BranchType].getName}).JOINED",
              "key" -> "#input.key"
            )
          ),
          "aggregator" -> s"T(${classOf[AggregateHelper].getName}).LAST",
          "windowLength" -> s"T(${classOf[Duration].getName}).parse('PT2H')",
          "aggregateBy" -> "#input.value"
        )
        .sink(EndNodeId, s"#$OutVariableName", "end")
    ))

    val key = "fooKey"
    val input1 = List(
      OneRecord(key, 0, -1),
      OneRecord(key, 2, -1)
    )
    val input2 = List(
      OneRecord(key, 1, 123)
    )

    val results = runProcess(process, input1, input2)

    val outValues = results.nodeResults(EndNodeId)
      .filter(_.variableTyped(KeyVariableName).contains(key))
      .map(_.variableTyped[java.lang.Integer](OutVariableName).get)

    outValues shouldEqual List(null, 123)
  }

  private def runProcess(testProcess: EspProcess, input1: List[OneRecord], input2: List[OneRecord]): TestResults[Any] = {
    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    val model = modelData(input1, input2, collectingListener)
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(model), model.processConfig, ExecutionConfigPreparer.unOptimizedChain(model, None))
    registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, Some(collectingListener.runId))
    stoppableEnv.executeAndWaitForFinished(testProcess.id)()
    collectingListener.results[Any]
  }

  private def modelData(input1: List[OneRecord], input2: List[OneRecord], collectingListener: ResultsCollectingListener) =
    LocalModelData(ConfigFactory.empty(), new OuterJoinTransformerSpec.Creator(input1, input2, collectingListener))

}

object OuterJoinTransformerSpec {

  class Creator(mainRecords: List[OneRecord], joinedRecords: List[OneRecord], collectingListener: ResultsCollectingListener) extends EmptyProcessConfigCreator {

    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "outer-join" -> WithCategories(new OuterJoinTransformer(None) {
          // We can't be sure that each main records will be consumed after matching joined records - main stream is consumed instantly.
          // But thanks to fact, that sliding aggregates handles out of order events, we just need to ensure that all joined records are consumed before main records.
          override protected def prepareAggregatorFunction(aggregator: Aggregator, stateTimeout: FiniteDuration)(implicit nodeId: ProcessCompilationError.NodeId):
          CoProcessFunction[ValueWithContext[String], ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]] = {
            val joinedSize = joinedRecords.size
            new CoProcessFunctionInterceptor(super.prepareAggregatorFunction(aggregator, stateTimeout)) with LazyLogging {

              @transient
              private lazy val addedToStateCount = new AtomicInteger(0)

              @transient
              private lazy val stateLock = new Object

              override protected def beforeProcessElement1(value: ValueWithContext[String]): Unit = {
                logger.info(s"OuterJoin: processing element1 with key: ${value.value} - waiting on all elements added to state...")
                while (isRunning && addedToStateCount.get() < joinedSize) {
                  stateLock.synchronized {
                    stateLock.wait(100)
                  }
                }
                logger.info(s"OuterJoin: processing element1 with key: ${value.value} - after wait")
              }

              override protected def afterProcessElement2(value: ValueWithContext[StringKeyedValue[AnyRef]]): Unit = {
                addedToStateCount.incrementAndGet()
                logger.info(s"OuterJoin: element2 processed: ${value.value} - notifying")
                stateLock.synchronized {
                  stateLock.notifyAll()
                }
              }

            }
          }
        }))

    override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
      Seq(collectingListener)

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] =
      Map(
        "start-main" -> WithCategories(NoParamSourceFactory(
          new EmitWatermarkAfterEachElementCollectionSource[OneRecord](mainRecords, OneRecord.timestampExtractor))),
        "start-joined" -> WithCategories(NoParamSourceFactory(
          new EmitWatermarkAfterEachElementCollectionSource[OneRecord](joinedRecords, OneRecord.timestampExtractor))))

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
      Map("end" -> WithCategories(SinkFactory.noParam(EmptySink)))

    override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
      ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  }

  object OneRecord {
    val timestampExtractor: AssignerWithPunctuatedWatermarks[OneRecord] = new BoundedOutOfOrdernessPunctuatedExtractor[OneRecord](1 * 3600 * 1000) {
      override def extractTimestamp(element: OneRecord, previousElementTimestamp: Long): Long = element.timeHours * 3600 * 1000
    }
  }

  case class OneRecord(key: String, timeHours: Int, value: Int)

}
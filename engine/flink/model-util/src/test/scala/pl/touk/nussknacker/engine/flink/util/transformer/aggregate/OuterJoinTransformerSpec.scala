package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory.NoParamSourceFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.exception.{BrieflyLoggingExceptionHandler, ConfigurableExceptionHandlerFactory}
import pl.touk.nussknacker.engine.flink.util.function.CoProcessFunctionInterceptor
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.{BlockingQueueSource, EmitWatermarkAfterEachElementCollectionSource}
import pl.touk.nussknacker.engine.flink.util.transformer.outer.{BranchType, OuterJoinTransformer}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.util.Collections
import java.util.Collections.{emptyList, singletonList}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.mapAsScalaMapConverter

class OuterJoinTransformerSpec extends FunSuite with FlinkSpec with Matchers with VeryPatientScalaFutures {

  import OuterJoinTransformerSpec._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val MainBranchId = "main"

  private val JoinedBranchId = "joined"

  private val JoinNodeId = "joined-node-id"

  private val EndNodeId = "end-node-id"

  private val KeyVariableName = "keyVar"

  private val OutVariableName = "outVar"

  test("join aggregate into main stream") {
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
          "aggregator" -> s"#AGG.map({last: #AGG.last, list: #AGG.list, approxCardinality: #AGG.approxCardinality, sum: #AGG.sum})",
          "windowLength" -> s"T(${classOf[Duration].getName}).parse('PT2H')",
          "aggregateBy" -> "{last: #input.value, list: #input.value, approxCardinality: #input.value, sum: #input.value } "
        )
        .sink(EndNodeId, s"#$OutVariableName", "end")
    ))

    val key = "fooKey"
    val input1 = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val input2 = List(
      OneRecord(key, 1, 123)
    )

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    val (id, stoppableEnv) = runProcess(process, input1, input2, collectingListener)

    input1.add(OneRecord(key, 0, -1))
    // We can't be sure that main records will be consumed after matching joined records so we need to wait for them.
    eventually {
      OuterJoinTransformerSpec.elementsAddedToState should have size input2.size
    }
    input1.add(OneRecord(key, 2, -1))
    input1.finish()

    stoppableEnv.waitForJobState(id.getJobID, process.id, ExecutionState.FINISHED)()

    val outValues = collectingListener.results[Any].nodeResults(EndNodeId)
      .filter(_.variableTyped(KeyVariableName).contains(key))
      .map(_.variableTyped[java.util.Map[String, AnyRef]](OutVariableName).get.asScala)

    outValues shouldEqual List(
      Map("approxCardinality" -> 0, "last" -> null, "list" -> emptyList(), "sum" -> 0),
      Map("approxCardinality" -> 1, "last" -> 123, "list" -> singletonList(123), "sum" -> 123)
    )
  }

  private def runProcess(testProcess: EspProcess, input1: BlockingQueueSource[OneRecord], input2: List[OneRecord], collectingListener: ResultsCollectingListener) = {
    val model = modelData(input1, input2, collectingListener)
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model), ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, DeploymentData.empty, Some(collectingListener.runId))
    val id = stoppableEnv.executeAndWaitForStart(testProcess.id)
    (id, stoppableEnv)
  }

  private def modelData(input1: BlockingQueueSource[OneRecord], input2: List[OneRecord], collectingListener: ResultsCollectingListener) =
    LocalModelData(ConfigFactory.empty(), new OuterJoinTransformerSpec.Creator(input1, input2, collectingListener))

}

object OuterJoinTransformerSpec {

  val elementsAddedToState = new ConcurrentLinkedQueue[StringKeyedValue[AnyRef]]()

  class Creator(mainRecordsSource: BlockingQueueSource[OneRecord], joinedRecords: List[OneRecord], collectingListener: ResultsCollectingListener) extends EmptyProcessConfigCreator {

    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "outer-join" -> WithCategories(new OuterJoinTransformer(None) {
          override protected def prepareAggregatorFunction(aggregator: Aggregator, stateTimeout: FiniteDuration, aggregateElementType: TypingResult, storedTypeInfo: TypeInformation[AnyRef])
                                                          (implicit nodeId: ProcessCompilationError.NodeId):
          CoProcessFunction[ValueWithContext[String], ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]] = {
            new CoProcessFunctionInterceptor(super.prepareAggregatorFunction(aggregator, stateTimeout, aggregateElementType, storedTypeInfo)) {
              override protected def afterProcessElement2(value: ValueWithContext[StringKeyedValue[AnyRef]]): Unit = {
                elementsAddedToState.add(value.value)
              }
            }
          }
        }))

    override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
      Seq(collectingListener)

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] =
      Map(
        "start-main" -> WithCategories(NoParamSourceFactory(mainRecordsSource)),
        "start-joined" -> WithCategories(NoParamSourceFactory(
          EmitWatermarkAfterEachElementCollectionSource.create[OneRecord](joinedRecords, _.timestamp, Duration.ofHours(1)))))

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
      Map("end" -> WithCategories(SinkFactory.noParam(EmptySink)))

    override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
      ConfigurableExceptionHandlerFactory(processObjectDependencies)

    override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig =
      super.expressionConfig(processObjectDependencies).copy(globalProcessVariables = Map("AGG" -> WithCategories(new AggregateHelper)))
  }

  case class OneRecord(key: String, timeHours: Int, value: Int) {
    def timestamp: Long = timeHours * 3600L * 1000
  }

}

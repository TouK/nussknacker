package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.{JobExecutionResult, JobID}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.{FlinkSpec, MiniClusterExecutionEnvironment}
import pl.touk.nussknacker.engine.flink.util.function.CoProcessFunctionInterceptor
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.{BlockingQueueSource, EmitWatermarkAfterEachElementCollectionSource}
import pl.touk.nussknacker.engine.flink.util.transformer.join.{BranchType, SingleSideJoinTransformer}
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.time.Duration
import java.util.Collections.{emptyList, singletonList}
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.Using

class SingleSideJoinTransformerSpec extends AnyFunSuite with FlinkSpec with Matchers with VeryPatientScalaFutures {

  import SingleSideJoinTransformerSpec._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val MainBranchId = "main"

  private val JoinedBranchId = "joined"

  private val JoinNodeId = "joined-node-id"

  private val EndNodeId = "end-node-id"

  private val KeyVariableName = "keyVar"

  private val OutVariableName = "outVar"

  test("join aggregate into main stream") {
    val process = ScenarioBuilder
      .streaming("sample-join-last")
      .sources(
        GraphBuilder
          .source("source", "start-main")
          .buildSimpleVariable("build-key", KeyVariableName, "#input.key".spel)
          .branchEnd(MainBranchId, JoinNodeId),
        GraphBuilder
          .source("joined-source", "start-joined")
          .branchEnd(JoinedBranchId, JoinNodeId),
        GraphBuilder
          .join(
            JoinNodeId,
            customElementName,
            Some(OutVariableName),
            List(
              MainBranchId -> List(
                "branchType" -> s"T(${classOf[BranchType].getName}).MAIN".spel,
                "key"        -> s"#$KeyVariableName".spel
              ),
              JoinedBranchId -> List(
                "branchType" -> s"T(${classOf[BranchType].getName}).JOINED".spel,
                "key"        -> "#input.key".spel
              )
            ),
            "aggregator" -> s"#AGG.map({last: #AGG.last, list: #AGG.list, approxCardinality: #AGG.approxCardinality, sum: #AGG.sum})".spel,
            "windowLength" -> s"T(${classOf[Duration].getName}).parse('PT2H')".spel,
            "aggregateBy" -> "{last: #input.value, list: #input.value, approxCardinality: #input.value, sum: #input.value } ".spel
          )
          .emptySink(EndNodeId, "dead-end")
      )

    val key    = "fooKey"
    val input1 = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val input2 = List(
      OneRecord(key, 1, 123)
    )

    Using.resource(ResultsCollectingListenerHolder.registerListener) { collectingListener =>
      withRunningScenario(process, input1, input2, collectingListener) { (jobId, stoppableEnv) =>
        input1.add(OneRecord(key, 0, -1))
        // We can't be sure that main records will be consumed after matching joined records so we need to wait for them.
        eventually {
          SingleSideJoinTransformerSpec.elementsAddedToState should have size input2.size
        }
        input1.add(OneRecord(key, 2, -1))
        input1.finish()

        stoppableEnv.waitForFinished(jobId)()

        val outValues = collectingListener.results
          .nodeResults(EndNodeId)
          .filter(_.variableTyped(KeyVariableName).contains(key))
          .map(_.variableTyped[java.util.Map[String, AnyRef]](OutVariableName).get.asScala)

        outValues shouldEqual List(
          Map("approxCardinality" -> 0, "last" -> null, "list" -> emptyList(), "sum"        -> 0),
          Map("approxCardinality" -> 1, "last" -> 123, "list"  -> singletonList(123), "sum" -> 123)
        )
      }
    }
  }

  private def withRunningScenario(
      testProcess: CanonicalProcess,
      input1: BlockingQueueSource[OneRecord],
      input2: List[OneRecord],
      collectingListener: ResultsCollectingListener[Any]
  )(action: (JobID, MiniClusterExecutionEnvironment) => Unit): Unit = {
    val model = modelData(input1, input2, collectingListener)
    flinkMiniCluster.withExecutionEnvironment { stoppableEnv =>
      val result = new FlinkScenarioUnitTestJob(model).run(testProcess, stoppableEnv.env)
      stoppableEnv.withJobRunning(result.getJobID)(action(result.getJobID, stoppableEnv))
    }
  }

  private def modelData(
      input1: BlockingQueueSource[OneRecord],
      input2: List[OneRecord],
      collectingListener: ResultsCollectingListener[Any]
  ) =
    LocalModelData(
      ConfigFactory.empty(),
      prepareComponents(input1, input2),
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
    )

}

object SingleSideJoinTransformerSpec {

  private val customElementName = "single-side-join-in-test"

  private val elementsAddedToState = new ConcurrentLinkedQueue[StringKeyedValue[AnyRef]]()

  private def prepareComponents(
      mainRecordsSource: BlockingQueueSource[OneRecord],
      joinedRecords: List[OneRecord]
  ): List[ComponentDefinition] = {
    ComponentDefinition("start-main", SourceFactory.noParamUnboundedStreamFactory[OneRecord](mainRecordsSource)) ::
      ComponentDefinition(
        "start-joined",
        SourceFactory.noParamUnboundedStreamFactory[OneRecord](
          EmitWatermarkAfterEachElementCollectionSource
            .create[OneRecord](joinedRecords, _.timestamp, Duration.ofHours(1))
        )
      ) ::
      ComponentDefinition("dead-end", SinkFactory.noParam(EmptySink)) ::
      joinComponentDefinition :: Nil
  }

  private val joinComponentDefinition =
    ComponentDefinition(
      customElementName,
      new SingleSideJoinTransformer(None) {

        override protected def prepareAggregatorFunction(
            aggregator: Aggregator,
            stateTimeout: FiniteDuration,
            aggregateElementType: TypingResult,
            storedTypeInfo: TypeInformation[AnyRef],
            convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
        )(implicit nodeId: NodeId): CoProcessFunction[ValueWithContext[String], ValueWithContext[
          StringKeyedValue[AnyRef]
        ], ValueWithContext[AnyRef]] = {
          new CoProcessFunctionInterceptor(
            super.prepareAggregatorFunction(
              aggregator,
              stateTimeout,
              aggregateElementType,
              storedTypeInfo,
              convertToEngineRuntimeContext
            )
          ) {
            override protected def afterProcessElement2(value: ValueWithContext[StringKeyedValue[AnyRef]]): Unit = {
              elementsAddedToState.add(value.value)
            }
          }
        }

      }
    )

  case class OneRecord(key: String, timeHours: Int, value: Int) {
    def timestamp: Long = timeHours * 3600L * 1000
  }

}

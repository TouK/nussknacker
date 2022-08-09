package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.function.ProcessFunctionInterceptor
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.BlockingQueueSource
import pl.touk.nussknacker.engine.flink.util.transformer.join.FullOuterJoinTransformer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.time.Duration
import scala.collection.JavaConverters._
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.mapAsScalaMapConverter

class FullOuterJoinTransformerSpec extends FunSuite with FlinkSpec with Matchers with VeryPatientScalaFutures {

  import FullOuterJoinTransformerSpec._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val JoinNodeId = "joined-node-id"

  private val EndNodeId = "end-node-id"

  private val KeyVariableName = "keyVar"

  private val OutVariableName = "outVar"

  private def performTest(input: List[Either[OneRecord, OneRecord]], expected: List[Map[String, AnyRef]]): Unit = {
    val MainBranchId = "main"
    val JoinedBranchId = "joined"

    val process =  EspProcess(MetaData("sample-join-last", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("source", "start-main")
        .buildSimpleVariable("build-key", KeyVariableName, "#input.key")
        .branchEnd(MainBranchId, JoinNodeId),
      GraphBuilder.source("joined-source", "start-joined")
        .branchEnd(JoinedBranchId, JoinNodeId),
      GraphBuilder
        .join(JoinNodeId, customElementName, Some(OutVariableName),
          List(
            MainBranchId -> List(
              "key" -> s"#$KeyVariableName",
              "aggregator" -> s"#AGG.map({last: #AGG.last, sum: #AGG.sum})",
              "aggregateBy" -> "{last: #input.value, sum: #input.value } "
            ),
            JoinedBranchId -> List(
              "key" -> "#input.key",
              "aggregator" -> s"#AGG.map({last: #AGG.last, list: #AGG.list, approxCardinality: #AGG.approxCardinality, sum: #AGG.sum})",
              "aggregateBy" -> "{last: #input.value, list: #input.value, approxCardinality: #input.value, sum: #input.value } "
            )
          ),
          "windowLength" -> s"T(${classOf[Duration].getName}).parse('PT20H')",
        )
        .emptySink(EndNodeId, "end")
    ))

    val input1 = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val input2 = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))

    var addedTo = 0

    def addTo1(elem: OneRecord): Unit = {
      input1.add(elem)
      addedTo += 1
      eventually {
        FullOuterJoinTransformerSpec.elementsAddedToState should have size addedTo
      }
    }

    def addTo2(elem: OneRecord): Unit = {
      input2.add(elem)
      addedTo += 1
      eventually {
        FullOuterJoinTransformerSpec.elementsAddedToState should have size addedTo
      }
    }

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    val (id, stoppableEnv) = runProcess(process, input1, input2, collectingListener)

    input.foreach {
      case Left(x) => addTo1(x)
      case Right(x) => addTo2(x)
    }

    input1.finish()
    input2.finish()


    stoppableEnv.waitForJobState(id.getJobID, process.id, ExecutionState.FINISHED)()

    val outValues = collectingListener.results[Any].nodeResults(EndNodeId)
      .map(_.variableTyped[java.util.Map[String, AnyRef]](OutVariableName).get.asScala)
      .map(_.mapValues{
        case x: java.util.Map[String@unchecked, AnyRef@unchecked] => x.asScala.asInstanceOf[AnyRef]
        case x => x
      })

    outValues shouldEqual expected
  }

  test("simple join") {
    val key = "key_foo"
    performTest(
      List(
        Left(OneRecord(key, 0, 7)),
        Right(OneRecord(key, 1, 12)),
        Left(OneRecord(key, 2, 51))
      ),
      List(
        Map("main" -> Map("last" -> 7, "sum" -> 7),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key),
        Map("main" -> Map("last" -> 7, "sum" -> 7),
          "joined" -> Map("last" -> 12, "list" -> List(12).asJava, "approxCardinality" -> 1, "sum" -> 12),
          "key" -> key),
        Map("main" -> Map("last" -> 51, "sum" -> 58),
          "joined" -> Map("last" -> 12, "list" -> List(12).asJava, "approxCardinality" -> 1, "sum" -> 12),
          "key" -> key),
      )
    )
  }

  test("many joined from the right") {
    val key = "key_goo"
    performTest(
      List(
        Left(OneRecord(key, 0, 11)),
        Right(OneRecord(key, 1, 1)),
        Right(OneRecord(key, 2, 2)),
        Right(OneRecord(key, 3, 3)),
        Right(OneRecord(key, 4, 4)),
        Right(OneRecord(key, 5, 5)),
        Left(OneRecord(key, 6, 11))
      ),
      List(
        Map("main" -> Map("last" -> 11, "sum" -> 11),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key),
        Map("main" -> Map("last" -> 11, "sum" -> 11),
          "joined" -> Map("last" -> 1, "list" -> List(1).asJava, "approxCardinality" -> 1, "sum" -> 1),
          "key" -> key),
        Map("main" -> Map("last" -> 11, "sum" -> 11),
          "joined" -> Map("last" -> 2, "list" -> List(1, 2).asJava, "approxCardinality" -> 2, "sum" -> 3),
          "key" -> key),
        Map("main" -> Map("last" -> 11, "sum" -> 11),
          "joined" -> Map("last" -> 3, "list" -> List(1, 2, 3).asJava, "approxCardinality" -> 3, "sum" -> 6),
          "key" -> key),
        Map("main" -> Map("last" -> 11, "sum" -> 11),
          "joined" -> Map("last" -> 4, "list" -> List(1, 2, 3, 4).asJava, "approxCardinality" -> 4, "sum" -> 10),
          "key" -> key),
        Map("main" -> Map("last" -> 11, "sum" -> 11),
          "joined" -> Map("last" -> 5, "list" -> List(1, 2, 3, 4, 5).asJava, "approxCardinality" -> 5, "sum" -> 15),
          "key" -> key),
        Map("main" -> Map("last" -> 11, "sum" -> 22),
          "joined" -> Map("last" -> 5, "list" -> List(1, 2, 3, 4, 5).asJava, "approxCardinality" -> 5, "sum" -> 15),
          "key" -> key),
      )
    )
  }

  test("many joined from the left") {
    val key = "key"
    performTest(
      List(
        Left(OneRecord(key, 0, -1)),
        Left(OneRecord(key, 1, -2)),
        Left(OneRecord(key, 2, -3)),
        Right(OneRecord(key, 3, 10)),
        Left(OneRecord(key, 4, -4)),
        Left(OneRecord(key, 5, -5)),
        Left(OneRecord(key, 6, -6))
      ),
      List(
        Map("main" -> Map("last" -> -1, "sum" -> -1),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key),
        Map("main" -> Map("last" -> -2, "sum" -> -3),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key),
        Map("main" -> Map("last" -> -3, "sum" -> -6),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key),
        Map("main" -> Map("last" -> -3, "sum" -> -6),
          "joined" -> Map("last" -> 10, "list" -> List(10).asJava, "approxCardinality" -> 1, "sum" -> 10),
          "key" -> key),
        Map("main" -> Map("last" -> -4, "sum" -> -10),
          "joined" -> Map("last" -> 10, "list" -> List(10).asJava, "approxCardinality" -> 1, "sum" -> 10),
          "key" -> key),
        Map("main" -> Map("last" -> -5, "sum" -> -15),
          "joined" -> Map("last" -> 10, "list" -> List(10).asJava, "approxCardinality" -> 1, "sum" -> 10),
          "key" -> key),
        Map("main" -> Map("last" -> -6, "sum" -> -21),
          "joined" -> Map("last" -> 10, "list" -> List(10).asJava, "approxCardinality" -> 1, "sum" -> 10),
          "key" -> key),
      )
    )
  }

  test("many joined both sides") {
    val key = "key"
    performTest(
      List(
        Left(OneRecord(key, 0, 0)),
        Left(OneRecord(key, 1, 1)),
        Left(OneRecord(key, 2, 2)),
        Right(OneRecord(key, 3, 3)),
        Right(OneRecord(key, 4, 4)),
        Right(OneRecord(key, 5, 5)),
        Left(OneRecord(key, 6, 6)),
        Left(OneRecord(key, 7, 7)),
        Left(OneRecord(key, 8, 8)),
        Right(OneRecord(key, 9, 9)),
        Right(OneRecord(key, 10, 10)),
        Right(OneRecord(key, 11, 11))
      ),
      List(
        Map("main" -> Map("last" -> 0, "sum" -> 0),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key),
        Map("main" -> Map("last" -> 1, "sum" -> 1),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key),
        Map("main" -> Map("last" -> 2, "sum" -> 3),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key),
        Map("main" -> Map("last" -> 2, "sum" -> 3),
          "joined" -> Map("last" -> 3, "list" -> List(3).asJava, "approxCardinality" -> 1, "sum" -> 3),
          "key" -> key),
        Map("main" -> Map("last" -> 2, "sum" -> 3),
          "joined" -> Map("last" -> 4, "list" -> List(3, 4).asJava, "approxCardinality" -> 2, "sum" -> 7),
          "key" -> key),
        Map("main" -> Map("last" -> 2, "sum" -> 3),
          "joined" -> Map("last" -> 5, "list" -> List(3, 4, 5).asJava, "approxCardinality" -> 3, "sum" -> 12),
          "key" -> key),
        Map("main" -> Map("last" -> 6, "sum" -> 9),
          "joined" -> Map("last" -> 5, "list" -> List(3, 4, 5).asJava, "approxCardinality" -> 3, "sum" -> 12),
          "key" -> key),
        Map("main" -> Map("last" -> 7, "sum" -> 16),
          "joined" -> Map("last" -> 5, "list" -> List(3, 4, 5).asJava, "approxCardinality" -> 3, "sum" -> 12),
          "key" -> key),
        Map("main" -> Map("last" -> 8, "sum" -> 24),
          "joined" -> Map("last" -> 5, "list" -> List(3, 4, 5).asJava, "approxCardinality" -> 3, "sum" -> 12),
          "key" -> key),
        Map("main" -> Map("last" -> 8, "sum" -> 24),
          "joined" -> Map("last" -> 9, "list" -> List(3, 4, 5, 9).asJava, "approxCardinality" -> 4, "sum" -> 21),
          "key" -> key),
        Map("main" -> Map("last" -> 8, "sum" -> 24),
          "joined" -> Map("last" -> 10, "list" -> List(3, 4, 5, 9, 10).asJava, "approxCardinality" -> 5, "sum" -> 31),
          "key" -> key),
        Map("main" -> Map("last" -> 8, "sum" -> 24),
          "joined" -> Map("last" -> 11, "list" -> List(3, 4, 5, 9, 10, 11).asJava, "approxCardinality" -> 6, "sum" -> 42),
          "key" -> key),
      )
    )
  }

  test("timeouts") {
    val key = "key"
    performTest(
      List(
        Left(OneRecord(key, 0, 1)),
        Right(OneRecord(key, 19, 2)),
        Right(OneRecord(key, 20, 3)),
        Right(OneRecord(key, 21, 4))
      ),
      List(
        Map("main" -> Map("last" -> 1, "sum" -> 1),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key),
        Map("main" -> Map("last" -> 1, "sum" -> 1),
          "joined" -> Map("last" -> 2, "list" -> List(2).asJava, "approxCardinality" -> 1, "sum" -> 2),
          "key" -> key),
        Map("main" -> Map("last" -> null, "sum" -> null),
          "joined" -> Map("last" -> 3, "list" -> List(2, 3).asJava, "approxCardinality" -> 2, "sum" -> 5),
          "key" -> key),
        Map("main" -> Map("last" -> null, "sum" -> null),
          "joined" -> Map("last" -> 4, "list" -> List(2, 3, 4).asJava, "approxCardinality" -> 3, "sum" -> 9),
          "key" -> key),
      )
    )
  }

  test("different keys") {
    val key1 = "key1"
    val key2 = "key2"
    performTest(
      List(
        Left(OneRecord(key1, 0, 0)),
        Right(OneRecord(key2, 1, 1)),
        Right(OneRecord(key1, 2, 2)),
        Left(OneRecord(key2, 3, 3))
      ),
      List(
        Map("main" -> Map("last" -> 0, "sum" -> 0),
          "joined" -> Map("last" -> null, "list" -> List().asJava, "approxCardinality" -> 0, "sum" -> null),
          "key" -> key1),
        Map("main" -> Map("last" -> null, "sum" -> null),
          "joined" -> Map("last" -> 1, "list" -> List(1).asJava, "approxCardinality" -> 1, "sum" -> 1),
          "key" -> key2),
        Map("main" -> Map("last" -> 0, "sum" -> 0),
          "joined" -> Map("last" -> 2, "list" -> List(2).asJava, "approxCardinality" -> 1, "sum" -> 2),
          "key" -> key1),
        Map("main" -> Map("last" -> 3, "sum" -> 3),
          "joined" -> Map("last" -> 1, "list" -> List(1).asJava, "approxCardinality" -> 1, "sum" -> 1),
          "key" -> key2),
      )
    )
  }

  test("input node named \"key\"") {
    val MainBranchId = "key"
    val JoinedBranchId = "joined"

    val process =  EspProcess(MetaData("sample-join-last", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("source", "start-main")
        .buildSimpleVariable("build-key", KeyVariableName, "#input.key")
        .branchEnd(MainBranchId, JoinNodeId),
      GraphBuilder.source("joined-source", "start-joined")
        .branchEnd(JoinedBranchId, JoinNodeId),
      GraphBuilder
        .join(JoinNodeId, customElementName, Some(OutVariableName),
          List(
            MainBranchId -> List(
              "key" -> s"#$KeyVariableName",
              "aggregator" -> s"#AGG.map({last: #AGG.last, sum: #AGG.sum})",
              "aggregateBy" -> "{last: #input.value, sum: #input.value } "
            ),
            JoinedBranchId -> List(
              "key" -> "#input.key",
              "aggregator" -> s"#AGG.map({last: #AGG.last, list: #AGG.list, approxCardinality: #AGG.approxCardinality, sum: #AGG.sum})",
              "aggregateBy" -> "{last: #input.value, list: #input.value, approxCardinality: #input.value, sum: #input.value } "
            )
          ),
          "windowLength" -> s"T(${classOf[Duration].getName}).parse('PT20H')",
        )
        .emptySink(EndNodeId, "end")
    ))

    val sourceFoo = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val sourceBar = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    val model = LocalModelData(ConfigFactory.empty(), new FullOuterJoinTransformerSpec.Creator(sourceFoo, sourceBar, collectingListener))
    val processValidator = model.prepareValidatorForCategory(None)
    val validationResult = processValidator.validate(process).result
    assert(validationResult.isInvalid)
  }


  test("input nodes with similar names") {
    val MainBranchId = "underscore_or_space"
    val JoinedBranchId = "underscore or space"

    val process =  EspProcess(MetaData("sample-join-last", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("source", "start-main")
        .buildSimpleVariable("build-key", KeyVariableName, "#input.key")
        .branchEnd(MainBranchId, JoinNodeId),
      GraphBuilder.source("joined-source", "start-joined")
        .branchEnd(JoinedBranchId, JoinNodeId),
      GraphBuilder
        .join(JoinNodeId, customElementName, Some(OutVariableName),
          List(
            MainBranchId -> List(
              "key" -> s"#$KeyVariableName",
              "aggregator" -> s"#AGG.map({last: #AGG.last, sum: #AGG.sum})",
              "aggregateBy" -> "{last: #input.value, sum: #input.value } "
            ),
            JoinedBranchId -> List(
              "key" -> "#input.key",
              "aggregator" -> s"#AGG.map({last: #AGG.last, list: #AGG.list, approxCardinality: #AGG.approxCardinality, sum: #AGG.sum})",
              "aggregateBy" -> "{last: #input.value, list: #input.value, approxCardinality: #input.value, sum: #input.value } "
            )
          ),
          "windowLength" -> s"T(${classOf[Duration].getName}).parse('PT20H')",
        )
        .emptySink(EndNodeId, "end")
    ))

    val sourceFoo = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val sourceBar = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    val model = LocalModelData(ConfigFactory.empty(), new FullOuterJoinTransformerSpec.Creator(sourceFoo, sourceBar, collectingListener))
    val processValidator = model.prepareValidatorForCategory(None)
    val validationResult = processValidator.validate(process).result
    assert(validationResult.isInvalid)
  }

  private def runProcess(testProcess: EspProcess, input1: BlockingQueueSource[OneRecord], input2: BlockingQueueSource[OneRecord], collectingListener: ResultsCollectingListener) = {
    val model = modelData(input1, input2, collectingListener)
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model), ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, DeploymentData.empty)
    val id = stoppableEnv.executeAndWaitForStart(testProcess.id)
    (id, stoppableEnv)
  }

  private def modelData(input1: BlockingQueueSource[OneRecord], input2: BlockingQueueSource[OneRecord], collectingListener: ResultsCollectingListener) = {
    val creator = new FullOuterJoinTransformerSpec.Creator(input1, input2, collectingListener)
    creator.resetElementsAdded()
    LocalModelData(ConfigFactory.empty(), creator)
  }

}

object FullOuterJoinTransformerSpec {

  private val customElementName = "single-side-join-in-test"

  val elementsAddedToState = new ConcurrentLinkedQueue[StringKeyedValue[AnyRef]]()

  class Creator (mainRecordsSource: BlockingQueueSource[OneRecord], joinedRecordsSource: BlockingQueueSource[OneRecord], collectingListener: ResultsCollectingListener) extends EmptyProcessConfigCreator {
    def resetElementsAdded(): Unit = {
      elementsAddedToState.clear()
    }

    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        customElementName -> WithCategories(new FullOuterJoinTransformer(None) {
          override protected def prepareAggregatorFunction(aggregator: Aggregator, stateTimeout: FiniteDuration, aggregateElementType: TypingResult,
                                                           storedTypeInfo: TypeInformation[AnyRef], convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext)
                                                          (implicit nodeId: NodeId):
          KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]] = {
            new ProcessFunctionInterceptor(super.prepareAggregatorFunction(aggregator, stateTimeout, aggregateElementType, storedTypeInfo, convertToEngineRuntimeContext)) {
              override protected def afterProcessElement(value: ValueWithContext[StringKeyedValue[AnyRef]]): Unit = {
                elementsAddedToState.add(value.value)
              }
            }
          }
        }))

    override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
      Seq(collectingListener)

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
      Map(
        "start-main" -> WithCategories(SourceFactory.noParam[OneRecord](mainRecordsSource)),
        "start-joined" -> WithCategories(SourceFactory.noParam[OneRecord](joinedRecordsSource))
      )

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
      Map("end" -> WithCategories(SinkFactory.noParam(EmptySink)))

  }

  case class OneRecord(key: String, timeHours: Int, value: Int) {
    def timestamp: Long = timeHours * 3600L * 1000
  }

}

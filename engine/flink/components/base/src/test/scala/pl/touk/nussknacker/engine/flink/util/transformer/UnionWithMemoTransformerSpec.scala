package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.util.KeyedValue
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.BlockingQueueSource
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.{util => jul}
import scala.collection.JavaConverters._

class UnionWithMemoTransformerSpec extends AnyFunSuite with FlinkSpec with Matchers with VeryPatientScalaFutures {

  import UnionWithMemoTransformerSpec._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val UnionNodeId = "joined-node-id"

  private val EndNodeId = "end-node-id"

  private val OutVariableName = "outVar"

  test("union with memo") {
    val BranchFooId = "foo"
    val BranchBarId = "bar"

    val process =  ScenarioBuilder.streaming("sample-union-memo").sources(
      GraphBuilder.source("start-foo", "start-foo")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "start-bar")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .join(UnionNodeId, "union-memo-test", Some(OutVariableName),
          List(
            BranchFooId -> List(
              "key" -> "#input.key",
              "value" -> "#input.value"
            ),
            BranchBarId -> List(
              "key" -> "#input.key",
              "value" -> "#input.value"
            )
          ),
          "stateTimeout" -> s"T(${classOf[Duration].getName}).parse('PT2H')"
        )
        .emptySink(EndNodeId, "end")
    )

    val key = "fooKey"
    val sourceFoo = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val sourceBar = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    withProcess(process, sourceFoo, sourceBar, collectingListener) {
      sourceFoo.add(OneRecord(key, 0, 123))
      eventually {
        UnionWithMemoTransformerSpec.elementsProcessed.asScala.toList shouldEqual List(BranchFooId -> 123)
      }
      sourceBar.add(OneRecord(key, 1, 234))
      eventually {
        UnionWithMemoTransformerSpec.elementsProcessed.asScala.toList shouldEqual List(BranchFooId -> 123, BranchBarId -> 234)
      }

      eventually {
        val nodeResults = collectingListener.results[Any].nodeResults
        val outValues = nodeResults(EndNodeId)
          .map(_.variableTyped[jul.Map[String@unchecked, AnyRef@unchecked]](OutVariableName).get.asScala)

        outValues shouldEqual List(
          Map("key" -> key, BranchFooId -> 123),
          Map("key" -> key, BranchFooId -> 123, BranchBarId -> 234)
        )
      }
    }
  }

  test("union with memo should handle input nodes named \"key\"") {
    val BranchFooId = UnionWithMemoTransformer.KeyField
    val BranchBarId = "bar"

    val process =  ScenarioBuilder.streaming("sample-union-memo").sources(
      GraphBuilder.source("start-foo", "start-foo")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "start-bar")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .join(UnionNodeId, "union-memo-test", Some(OutVariableName),
          List(
            BranchFooId -> List(
              "key" -> "#input.key",
              "value" -> "#input.value"
            ),
            BranchBarId -> List(
              "key" -> "#input.key",
              "value" -> "#input.value"
            )
          ),
          "stateTimeout" -> s"T(${classOf[Duration].getName}).parse('PT2H')"
        ).emptySink(EndNodeId, "end")
    )

    val sourceFoo = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val sourceBar = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    val model = LocalModelData(ConfigFactory.empty(), new UnionWithMemoTransformerSpec.Creator(sourceFoo, sourceBar, collectingListener))
    val processValidator = model.prepareValidatorForCategory(None)
    val validationResult = processValidator.validate(process).result

    val expectedMessage = s"""Input node can not be named "${UnionWithMemoTransformer.KeyField}"""
    validationResult should matchPattern {
      case Invalid(NonEmptyList(CustomNodeError(UnionNodeId, expectedMessage, None), Nil)) =>
    }
  }


  test("union with memo should handle input nodes with similar names") {
    val BranchFooId = "underscore_or_space"
    val BranchBarId = "underscore or space"

    val process =  ScenarioBuilder.streaming("sample-union-memo").sources(
      GraphBuilder.source("start-foo", "start-foo")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "start-bar")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .join(UnionNodeId, "union-memo-test", Some(OutVariableName),
          List(
            BranchFooId -> List(
              "key" -> "#input.key",
              "value" -> "#input.value"
            ),
            BranchBarId -> List(
              "key" -> "#input.key",
              "value" -> "#input.value"
            )
          ),
          "stateTimeout" -> s"T(${classOf[Duration].getName}).parse('PT2H')"
        ).emptySink(EndNodeId, "end")
    )

    val sourceFoo = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))
    val sourceBar = BlockingQueueSource.create[OneRecord](_.timestamp, Duration.ofHours(1))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)

    val model = LocalModelData(ConfigFactory.empty(), new UnionWithMemoTransformerSpec.Creator(sourceFoo, sourceBar, collectingListener))
    val processValidator = model.prepareValidatorForCategory(None)
    val validationResult = processValidator.validate(process).result

    val expectedMessage = s"""Nodes "$BranchFooId", "$BranchBarId" have too similar names"""
    validationResult should matchPattern {
      case Invalid(NonEmptyList(CustomNodeError(UnionNodeId, expectedMessage, None), Nil)) =>
    }
  }

  private def withProcess(testProcess: CanonicalProcess, sourceFoo: BlockingQueueSource[OneRecord], sourceBar: BlockingQueueSource[OneRecord],
                          collectingListener: ResultsCollectingListener)(action: => Unit): Unit = {
    val model = LocalModelData(ConfigFactory.empty(), new UnionWithMemoTransformerSpec.Creator(sourceFoo, sourceBar, collectingListener))
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model), ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, DeploymentData.empty)
    stoppableEnv.withJobRunning(testProcess.id)(action)
  }
}

object UnionWithMemoTransformerSpec {

  val elementsProcessed = new ConcurrentLinkedQueue[(String, AnyRef)]()

  class Creator(sourceFoo: BlockingQueueSource[OneRecord], sourceBar: BlockingQueueSource[OneRecord], collectingListener: ResultsCollectingListener) extends EmptyProcessConfigCreator {

    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "union-memo-test" -> WithCategories(new UnionWithMemoTransformer(None) {
          override protected val mapElement: ValueWithContext[KeyedValue[String, (String, AnyRef)]] => ValueWithContext[KeyedValue[String, (String, AnyRef)]] = (v: ValueWithContext[KeyedValue[String, (String, AnyRef)]]) => {
            elementsProcessed.add(v.value.value)
            v
          }
        }))

    override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
      Seq(collectingListener)

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
      Map(
        "start-foo" -> WithCategories(SourceFactory.noParam[OneRecord](sourceFoo)),
        "start-bar" -> WithCategories(SourceFactory.noParam[OneRecord](sourceBar)))

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
      Map("end" -> WithCategories(SinkFactory.noParam(EmptySink)))
  }

  case class OneRecord(key: String, timeHours: Int, value: Int) {
    def timestamp: Long = timeHours * 3600L * 1000
  }

}

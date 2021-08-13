package pl.touk.nussknacker.engine.flink.util.transformer.aggregate


import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.{util => jul}
import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory.NoParamSourceFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.exception.{BrieflyLoggingExceptionHandler, ConfigurableExceptionHandlerFactory}
import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValue
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.BlockingQueueSource
import pl.touk.nussknacker.engine.flink.util.transformer.UnionWithMemoTransformer
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

import scala.collection.JavaConverters._

class UnionWithMemoTransformerSpec extends FunSuite with FlinkSpec with Matchers with VeryPatientScalaFutures {

  import UnionWithMemoTransformerSpec._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val BranchFooId = "foo"

  private val BranchBarId = "bar"

  private val UnionNodeId = "joined-node-id"

  private val EndNodeId = "end-node-id"

  private val OutVariableName = "outVar"

  test("union with memo") {
    val process =  EspProcess(MetaData("sample-union-memo", StreamMetaData()), ExceptionHandlerRef(List.empty), NonEmptyList.of[SourceNode](
      GraphBuilder.source("start-foo", "start-foo")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "start-bar")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .branch(UnionNodeId, "union-memo", Some(OutVariableName),
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
        .sink(EndNodeId, s"#$OutVariableName", "end")
    ))

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

  private def withProcess(testProcess: EspProcess, sourceFoo: BlockingQueueSource[OneRecord], sourceBar: BlockingQueueSource[OneRecord],
                          collectingListener: ResultsCollectingListener)(action: => Unit): Unit = {
    val model = LocalModelData(ConfigFactory.empty(), new UnionWithMemoTransformerSpec.Creator(sourceFoo, sourceBar, collectingListener))
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model), ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, DeploymentData.empty, Some(collectingListener.runId))
    stoppableEnv.withJobRunning(testProcess.id)(action)
  }
}

object UnionWithMemoTransformerSpec {

  val elementsProcessed = new ConcurrentLinkedQueue[(String, AnyRef)]()

  class Creator(sourceFoo: BlockingQueueSource[OneRecord], sourceBar: BlockingQueueSource[OneRecord], collectingListener: ResultsCollectingListener) extends EmptyProcessConfigCreator {

    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "union-memo" -> WithCategories(new UnionWithMemoTransformer(None) {
          override protected val mapElement: ValueWithContext[KeyedValue[String, (String, AnyRef)]] => ValueWithContext[KeyedValue[String, (String, AnyRef)]] = (v: ValueWithContext[KeyedValue[String, (String, AnyRef)]]) => {
            elementsProcessed.add(v.value.value)
            v
          }
        }))

    override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
      Seq(collectingListener)

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] =
      Map(
        "start-foo" -> WithCategories(NoParamSourceFactory(sourceFoo)),
        "start-bar" -> WithCategories(NoParamSourceFactory(sourceBar)))

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
      Map("end" -> WithCategories(SinkFactory.noParam(EmptySink)))

    override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
      ConfigurableExceptionHandlerFactory(processObjectDependencies)

  }

  case class OneRecord(key: String, timeHours: Int, value: Int) {
    def timestamp: Long = timeHours * 3600L * 1000
  }

}
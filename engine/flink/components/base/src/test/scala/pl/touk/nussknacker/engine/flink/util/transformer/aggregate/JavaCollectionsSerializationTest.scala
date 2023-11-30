package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.{
  EmptyProcessConfigCreator,
  ProcessObjectDependencies,
  SourceFactory,
  WithCategories
}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.DelayTransformer
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class JavaCollectionsSerializationTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  private val processId = "aggregateFilterProcess"

  private val process: CanonicalProcess =
    ScenarioBuilder
      .streaming(processId)
      .parallelism(1)
      .source("start", "start")
      .customNodeNoOutput("delay", "delay", "key" -> "#input.id", "delay" -> "T(java.time.Duration).parse('PT30M')")
      .emptySink("end", "dead-end")

  // In Scala 2.13 all java collections class wrappers were rewritten from case class to regular class. Now kryo does not
  // serialize them properly, so JavaWrapperScala2_13Registrar class was added to fix this issue. This test verifies
  // if we can serialize and deserialize records properly.
  test("should serialize record with java map, list and set") {
    val record = Record(
      id = "2",
      map = mutable.Map(1 -> "a").asJava,
      list = mutable.ListBuffer("abc").asJava,
      set = mutable.Set("def").asJava
    )

    val model = modelData(List(record))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    runProcess(model, process, collectingListener)

    val result = collectingListener
      .results[Any]
      .nodeResults("end")
      .map {
        _.variableTyped("input")
      }

    result shouldBe List(Some(record))
  }

  def modelData(list: List[Record] = List()): LocalModelData = LocalModelData(
    ConfigFactory
      .empty()
      .withValue("useTypingResultTypeInformation", fromAnyRef(true)),
    new AggregateCreator(list)
  )

  protected def runProcess(
      model: LocalModelData,
      testProcess: CanonicalProcess,
      collectingListener: ResultsCollectingListener
  ): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(
      new FlinkProcessCompiler(model) {
        override protected def adjustListeners(
            defaults: List[ProcessListener],
            processObjectDependencies: ProcessObjectDependencies
        ): List[ProcessListener] = {
          collectingListener :: defaults
        }
      },
      ExecutionConfigPreparer.unOptimizedChain(model)
    )
    registrar.register(stoppableEnv, testProcess, ProcessVersion.empty, DeploymentData.empty)
    stoppableEnv.executeAndWaitForFinished(testProcess.id)()
  }

}

class AggregateCreator(input: List[Record]) extends EmptyProcessConfigCreator {

  override def sourceFactories(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = {
    val inputType = Typed.fromDetailedType[List[Record]]
    Map(
      "start" -> WithCategories.anyCategory(
        SourceFactory.noParam[Record](
          CollectionSource[Record](input, None, inputType)(TypeInformation.of(classOf[Record]))
        )
      )
    )
  }

  override def customStreamTransformers(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map("delay" -> WithCategories.anyCategory(new DelayTransformer))
  }

}

case class Record(id: String, map: java.util.Map[Int, String], list: java.util.List[String], set: java.util.Set[String])

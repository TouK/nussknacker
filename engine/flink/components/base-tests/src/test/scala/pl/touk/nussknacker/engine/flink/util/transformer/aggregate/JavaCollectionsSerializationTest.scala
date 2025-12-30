package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}

import java.util.Collections

class JavaCollectionsSerializationTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  private val processId = "aggregateFilterProcess"

  private val process: CanonicalProcess =
    ScenarioBuilder
      .streaming(processId)
      .parallelism(1)
      .source("start", "start")
      .customNodeNoOutput(
        "delay",
        "delay",
        "key"   -> "#input.id".spel,
        "delay" -> "T(java.time.Duration).parse('PT30M')".spel
      )
      .emptySink("end", "dead-end")

  test("should serialize record with java map, list and set") {
    val record = new Record(
      id = "2",
      map = Collections.singletonMap(1, "a"),
      list = Collections.singletonList("abc"),
      set = Collections.singleton("def")
    )

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(collectingListener, List(record))

      runScenario(model, process)

      val result = collectingListener.results
        .nodeResults(NodeId("end"))
        .map(_.variableTyped[Record]("input"))

      result shouldBe List(Some(record))
    }
  }

  def modelData(collectingListener: ResultsCollectingListener[Any], list: List[Record] = List()): LocalModelData = {
    val sourceComponent = SourceFactory.noParamUnboundedStreamFactory[Record](
      CollectionSource[Record](list, None, Typed.fromDetailedType[List[Record]])
    )
    LocalModelData(
      ConfigFactory.empty(),
      ComponentDefinition(
        "start",
        sourceComponent
      ) :: FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
      new ConfigCreatorWithCollectingListener(collectingListener)
    )
  }

  protected def runScenario(
      model: LocalModelData,
      testScenario: CanonicalProcess
  ): Unit = {
    flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
      val executionResult = new FlinkScenarioUnitTestJob(model).run(testScenario, env)
      flinkMiniCluster.waitForJobIsFinished(executionResult.getJobID)
    }
  }

}

class Record(
    val id: String,
    val map: java.util.Map[Int, String],
    val list: java.util.List[String],
    val set: java.util.Set[String]
) extends Serializable {

  def this() = this(null, null, null, null)

  private def canEqual(other: Any): Boolean = other.isInstanceOf[Record]

  override def equals(other: Any): Boolean = other match {
    case that: Record =>
      that.canEqual(this) &&
      id == that.id &&
      map == that.map &&
      list == that.list &&
      set == that.set
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(id, map, list, set)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

}

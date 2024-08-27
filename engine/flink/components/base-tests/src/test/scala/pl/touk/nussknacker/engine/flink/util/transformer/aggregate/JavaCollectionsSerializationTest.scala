package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.spel.SpelExtension._
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
      .customNodeNoOutput(
        "delay",
        "delay",
        "key"   -> "#input.id".spel,
        "delay" -> "T(java.time.Duration).parse('PT30M')".spel
      )
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

    val collectingListener = ResultsCollectingListenerHolder.registerListener
    val model              = modelData(collectingListener, List(record))

    runProcess(model, process)

    val result = collectingListener.results
      .nodeResults("end")
      .map(_.variableTyped[Record]("input"))

    result shouldBe List(Some(record))
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

  protected def runProcess(
      model: LocalModelData,
      testProcess: CanonicalProcess
  ): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(stoppableEnv, model)(testProcess)
    stoppableEnv.executeAndWaitForFinished(testProcess.name.value)()
  }

}

case class Record(id: String, map: java.util.Map[Int, String], list: java.util.List[String], set: java.util.Set[String])

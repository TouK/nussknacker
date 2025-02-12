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
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Random

class JavaCollectionsSerializationTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  private val processId = "aggregateFilterProcess"

  private def process(expressionOption: Option[Expression] = None): CanonicalProcess = {
    val scenario = ScenarioBuilder
      .streaming(processId)
      .parallelism(1)
      .source("start", "start")

    expressionOption
      .map(expression => scenario.buildSimpleVariable("mapVariable", "mapVariable", expression))
      .getOrElse(scenario)
      .customNodeNoOutput(
        "delay",
        "delay",
        "key"   -> "#input.id".spel,
        "delay" -> "T(java.time.Duration).parse('PT30M')".spel
      )
      .emptySink("end", "dead-end")
  }

  private val record = Record(
    id = "2",
    map = mutable.Map(1 -> "a").asJava,
    list = mutable.ListBuffer("abc").asJava,
    set = mutable.Set("def").asJava
  )

  // In Scala 2.13 all java collections class wrappers were rewritten from case class to regular class. Now kryo does not
  // serialize them properly, so JavaWrapperScala2_13Registrar class was added to fix this issue. This test verifies
  // if we can serialize and deserialize records properly.
  test("should serialize record with java map, list and set") {

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(collectingListener, List(record))

      runScenario(model, process())

      val result = collectingListener.results
        .nodeResults("end")
        .map(_.variableTyped[Record]("input"))

      result shouldBe List(Some(record))
    }
  }

  test("should serialize java map without changing fields order") {

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(collectingListener, List(record))

      val sampleMap =
        ('a' to 'z')
          .map(x => x -> Random.nextInt())
          .sortBy(_._2)

      runScenario(
        model,
        process(
          Some(
            sampleMap
              .map { case (c, i) => s""""$c" : $i""" }
              .mkString("{", ",", "}")
              .spel
          )
        )
      )

      val linkedHashMap = new util.LinkedHashMap[String, AnyRef]()
      sampleMap.foreach { case (char, int) => linkedHashMap.put(s"$char", Integer.valueOf(int)) }

      val result = collectingListener.results
        .nodeResults("end")
        .map(_.variableTyped[Map[_, _]]("mapVariable"))

      result shouldBe List(Some(linkedHashMap))

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

case class Record(id: String, map: java.util.Map[Int, String], list: java.util.List[String], set: java.util.Set[String])

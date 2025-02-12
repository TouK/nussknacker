package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, Inside}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps._
import pl.touk.nussknacker.engine.testing.LocalModelData

class FlinkScenarioJobSpec extends AnyFlatSpec with Matchers with Inside with BeforeAndAfterAll {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  it should "be able to compile and serialize services" in {
    flinkMiniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      val process =
        ScenarioBuilder
          .streaming("proc1")
          .source("id", "input")
          .filter("filter1", "#sum(#input.![value1]) > 24".spel)
          .processor("proc2", "logService", "all" -> "#distinct(#input.![value2])".spel)
          .emptySink("out", "monitor")

      val modelData = LocalModelData(ConfigFactory.empty(), List.empty, configCreator = new SimpleProcessConfigCreator)
      val executionResult = new FlinkScenarioJob(modelData).run(
        process,
        ProcessVersion.empty,
        DeploymentData.empty,
        env
      )
      flinkMiniClusterWithServices.waitForJobIsFinished(executionResult.getJobID)
    }
  }

}

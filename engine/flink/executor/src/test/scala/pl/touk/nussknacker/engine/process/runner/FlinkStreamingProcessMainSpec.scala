package pl.touk.nussknacker.engine.process.runner

import io.circe.Encoder
import io.circe.syntax._
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration

class FlinkStreamingProcessMainSpec extends AnyFlatSpec with Matchers with Inside {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  object TestFlinkStreamingProcessMain extends BaseFlinkStreamingProcessMain {

    override protected def getExecutionEnvironment: StreamExecutionEnvironment = {
      StreamExecutionEnvironment.getExecutionEnvironment(FlinkTestConfiguration.configuration())
    }

  }

  it should "be able to compile and serialize services" in {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .filter("filter1", "#sum(#input.![value1]) > 24".spel)
        .processor("proc2", "logService", "all" -> "#distinct(#input.![value2])".spel)
        .emptySink("out", "monitor")

    TestFlinkStreamingProcessMain.main(
      Array(
        process.asJson.spaces2,
        Encoder[ProcessVersion].apply(ProcessVersion.empty).noSpaces,
        Encoder[DeploymentData].apply(DeploymentData.empty).noSpaces
      )
    )
  }

}

package pl.touk.nussknacker.engine.kafka.signal

import org.scalatest.{BeforeAndAfterEach, Inside}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.process.runner.FlinkTestMain
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.ThreadUtils

//We want to make sure that scenario with Kafka signal is testable from UI
class KafkaSignalInTestSpec extends AnyFunSuite with Matchers with Inside with BeforeAndAfterEach with KafkaSpec {

  test("be able to test process with signals") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .customNodeNoOutput("cid", "signalReader")
        .processorEnd("out", "logService", "all" -> "#input")

    val modelData = LocalModelData(config, new KafkaSignalsCreator(Nil))
    val testData = TestData.newLineSeparated("0|1|2|3|4|5|6")

    val results = ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(modelData, process, testData, FlinkTestConfiguration.configuration(), identity)
    }

    val nodeResults = results.nodeResults
    nodeResults("out") should have length 1
  }

}

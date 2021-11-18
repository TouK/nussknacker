package pl.touk.nussknacker.engine.kafka.signal

import org.scalatest.{BeforeAndAfterEach, FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SimpleRecord
import pl.touk.nussknacker.engine.process.runner.FlinkTestMain
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.util.Date

//We want to make sure that scenario with Kafka signal is testable from UI
class KafkaSignalInTestSpec extends FunSuite with Matchers with Inside with BeforeAndAfterEach with KafkaSpec {

  test("be able to test process with signals") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .source("id", "input")
        .customNodeNoOutput("cid", "signalReader")
        .processorEnd("out", "logService", "all" -> "#input")

    val modelData = LocalModelData(config, new KafkaSignalsCreator(Nil))
    val testData = TestData.newLineSeparated("0|1|2|3|4|5|6")

    val results = ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(modelData, ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).noSpaces,
        testData, FlinkTestConfiguration.configuration(), identity)
    }

    val nodeResults = results.nodeResults
    nodeResults("out") should have length 1

  }



}

package pl.touk.nussknacker.engine.baseengine.kafka

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.scalatest.{FunSuite, Matchers}
import org.springframework.util.StreamUtils
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.concurrent.Promise
import scala.util.Try

class NuKafkaEngineBinTest extends FunSuite with KafkaSpec with Matchers with LazyLogging with VeryPatientScalaFutures {

  private val inputTopic = "input"

  private val outputTopic = "output"

  // FIXME: fix NoClassDefFoundError: org/apache/flink/streaming/api/functions/sink/SinkFunction
  ignore("should run scenario and pass data to output ") {
    kafkaClient.createTopic(inputTopic)
    kafkaClient.createTopic(outputTopic, 1)

    val scenario = EspProcessBuilder
        .id("test")
        .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
        .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")
    val jsonFile = saveScenarioToTmp(scenario)

    val engineExitCodePromise = Promise[Int]
    // Thread was used instead of Future to make possible to interrupt it
    val thread = new Thread(() => {
      engineExitCodePromise.complete(Try {
        val process = Runtime.getRuntime.exec(Array(
          shellScriptPath.toString,
          jsonFile.toString))
        logger.info(s"Started engine process with pid: ${process.pid()}")
        StreamUtils.copy(process.getInputStream, System.out)
        StreamUtils.copy(process.getErrorStream, System.err)
        process.waitFor()
        process.exitValue()
      })
    })

    try {
      thread.start()

      val input = """{"foo": "ping"}"""
      kafkaClient.sendMessage(inputTopic, input).futureValue

//      val messages = kafkaClient.createConsumer().consume(outputTopic).take(1).map(rec => new String(rec.message()))
//      messages shouldBe List(input)
    } finally {
//      thread.interrupt()
    }

    engineExitCodePromise.future.futureValue shouldEqual 0 // success exit code
  }

  private def saveScenarioToTmp(scenario: EspProcess): File = {
    val canonicalScenario = ProcessCanonizer.canonize(scenario)
    val json = ProcessMarshaller.toJson(canonicalScenario)
    val jsonFile = File.createTempFile(getClass.getSimpleName, ".json")
    jsonFile.deleteOnExit()
    FileUtils.write(jsonFile, json.toString(), StandardCharsets.UTF_8)
    jsonFile
  }

  private def shellScriptPath: Path = {
    val targetItClassesDir = Path.of(getClass.getResource("/").toURI)
    val baseModuleDir = targetItClassesDir.getParent.getParent.getParent.getParent
    val stageDir = baseModuleDir.resolve("kafka/target/universal/stage")
    stageDir.resolve("bin/run.sh")
  }

}
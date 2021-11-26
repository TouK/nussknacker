package pl.touk.nussknacker.engine.baseengine.kafka

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.scalatest.{FunSuite, Matchers}
import org.springframework.util.StreamUtils
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils.richConsumer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.io.{File, IOException}
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

class NuKafkaRuntimeBinTest extends FunSuite with KafkaSpec with Matchers with LazyLogging with VeryPatientScalaFutures {

  private val inputTopic = "input"

  private val outputTopic = "output"

  test("should run scenario and pass data to output ") {
    kafkaClient.createTopic(inputTopic)
    kafkaClient.createTopic(outputTopic, 1)

    val scenario = EspProcessBuilder
        .id("test")
        .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
        .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")
    val jsonFile = saveScenarioToTmp(scenario)

    @volatile var process: Process = null
    val runtimeExitCodeFuture = Future {
      process = Runtime.getRuntime.exec(Array(
        shellScriptPath.toString,
        jsonFile.toString), Array(s"KAFKA_ADDRESS=${kafkaZookeeperServer.kafkaAddress}"))
      logger.info(s"Started kafka runtime process with pid: ${process.pid()}")
      try {
        StreamUtils.copy(process.getInputStream, System.out)
        StreamUtils.copy(process.getErrorStream, System.err)
      } catch {
        case ex: IOException => // ignore Stream closed
      }
      process.waitFor()
      process.exitValue()
    }

    try {
      val input =
        """{"foo":"ping"}""".stripMargin
      kafkaClient.sendMessage(inputTopic, input).futureValue

      // we check if future not completed with failure instantly to not shadow true failure by consume timeout
      runtimeExitCodeFuture.value.flatMap(_.failed.toOption)  shouldBe empty
      val messages = kafkaClient.createConsumer().consume(outputTopic, secondsToWait = 60).take(1).map(rec => new String(rec.message())).toList
      messages shouldBe List(input)
    } catch {
      case NonFatal(ex) =>
        if (process != null) {
          // thread dump
          Runtime.getRuntime.exec(s"kill -3 ${process.pid()}")
          // wait a while to make sure that stack trace is presented in logs
          Thread.sleep(3000)
        }
        throw ex
    } finally {
      if (process != null) {
        process.destroy()
      }
    }

    runtimeExitCodeFuture.futureValue shouldEqual 143 // success exit code TODO: shouldn't be just 0?
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
    val liteKafkaModuleDir = targetItClassesDir.getParent.getParent.getParent.getParent
    val stageDir = liteKafkaModuleDir.resolve("runtime/target/universal/stage")
    stageDir.resolve("bin/run.sh")
  }

}
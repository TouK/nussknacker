package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FunSuite, Matchers}
import org.springframework.util.StreamUtils
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.io.IOException
import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class NuKafkaRuntimeBinTest extends FunSuite with KafkaSpec with NuKafkaRuntimeTestMixin with Matchers with LazyLogging with VeryPatientScalaFutures {

  override protected def kafkaBoostrapServer: String = kafkaServer.kafkaAddress

  test("should run scenario and pass data to output ") {
    val fixture = prepareTestCaseFixture("json-ping-pong", NuKafkaRuntimeTestSamples.jsonPingPongScenario)

    val shellScriptArgs = Array(shellScriptPath.toString, fixture.scenarioFile.toString, deploymentDataFile.toString)
    val shellScriptEnvs = Array(
      s"KAFKA_ADDRESS=$kafkaBoostrapServer",
      // random management port to avoid clashing of ports
      "CONFIG_FORCE_akka_management_http_port=0",
      // It looks like github-actions doesn't look binding to 0.0.0.0, was problems like: Bind failed for TCP channel on endpoint [/10.1.0.183:0]
      "CONFIG_FORCE_akka_management_http_hostname=127.0.0.1"
    )
    withProcessExecutedInBackground(shellScriptArgs, shellScriptEnvs,
      {
        kafkaClient.sendMessage(fixture.inputTopic, NuKafkaRuntimeTestSamples.jsonPingMessage).futureValue
      },
      {
        val messages = kafkaClient.createConsumer().consume(fixture.outputTopic, secondsToWait = 60).take(1).map(rec => new String(rec.message())).toList
        messages shouldBe List(NuKafkaRuntimeTestSamples.jsonPingMessage)
      })
  }

  private def withProcessExecutedInBackground(shellScriptArgs: Array[String], shellScriptEnvs: Array[String],
                                              executeBeforeProcessStatusCheck: => Unit,
                                              executeAfterProcessStatusCheck: => Unit): Unit = {
    @volatile var process: Process = null
    val processExitCodeFuture = Future {
      process = Runtime.getRuntime.exec(shellScriptArgs,
        shellScriptEnvs)
      logger.info(s"Started kafka runtime process with pid: ${process.pid()}")
      try {
        StreamUtils.copy(process.getInputStream, System.out)
        StreamUtils.copy(process.getErrorStream, System.err)
      } catch {
        case _: IOException => // ignore Stream closed
      }
      process.waitFor()
      process.exitValue()
    }

    try {
      executeBeforeProcessStatusCheck
      checkIfFailedInstantly(processExitCodeFuture)
      executeAfterProcessStatusCheck
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

    processExitCodeFuture.futureValue shouldEqual 143 // success exit code TODO: shouldn't be just 0?
  }

  private def checkIfFailedInstantly(future: Future[Int]): Unit = {
    future.value match {
      case Some(tryValue) =>
        // If completed with failure instantly, fail to not shadow true failure by consume timeout
        tryValue.failed.toOption shouldBe empty
      case None =>
        // If not completed instantly but eventually completed with failure, we at least print error on console
        future.failed.foreach { ex =>
          ex.printStackTrace()
        }
    }
  }

  private def shellScriptPath: Path = {
    val targetItClassesDir = Path.of(getClass.getResource("/").toURI)
    val liteKafkaModuleDir = targetItClassesDir.getParent.getParent.getParent.getParent
    val stageDir = liteKafkaModuleDir.resolve("runtime/target/universal/stage")
    stageDir.resolve("bin/run.sh")
  }

}
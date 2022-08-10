package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers.{Container, GenericContainer, MultipleContainers}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.apache.avro.generic.GenericRecord
import org.scalatest.{FunSuite, Matchers}
import org.springframework.util.StreamUtils
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.io.IOException
import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

class NuKafkaRuntimeBinTest extends FunSuite with BaseNuKafkaRuntimeDockerTest with Matchers with LazyLogging with VeryPatientScalaFutures {

  private var inputSchemaId: Int = _

  private var outputSchemaId: Int = _

  private def mappedSchemaRegistryAddress = s"http://localhost:${schemaRegistryContainer.mappedPort(schemaRegistryPort)}"

  private val schemaRegistryHostname = "schemaregistry"
  private val schemaRegistryPort = 8081

  private val schemaRegistryContainer = {
    val container = GenericContainer(
      "confluentinc/cp-schema-registry:7.2.1",
      exposedPorts = Seq(schemaRegistryPort),
      env = Map(
        "SCHEMA_REGISTRY_HOST_NAME" -> schemaRegistryHostname,
        "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> dockerNetworkKafkaBoostrapServer)
    )
    configureNetwork(container, schemaRegistryHostname)
    container
  }

  override val container: Container = {
    kafkaContainer.start() // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture = prepareTestCaseFixture("ping-pong", NuKafkaRuntimeTestSamples.avroPingPongScenario)
    registerSchemas()
    MultipleContainers(kafkaContainer, schemaRegistryContainer)
  }

  test("should run scenario and pass data to output ") {

    val shellScriptArgs = Array(shellScriptPath.toString, fixture.scenarioFile.toString, deploymentDataFile.toString)
    val shellScriptEnvs = Array(
      s"KAFKA_ADDRESS=$kafkaBoostrapServer",
      "KAFKA_AUTO_OFFSET_RESET=earliest",
      s"SCHEMA_REGISTRY_URL=$mappedSchemaRegistryAddress",
      // random management port to avoid clashing of ports
      "CONFIG_FORCE_akka_management_http_port=0",
      // It looks like github-actions doesn't look binding to 0.0.0.0, was problems like: Bind failed for TCP channel on endpoint [/10.1.0.183:0]
      "CONFIG_FORCE_akka_management_http_hostname=127.0.0.1"
    )
    withProcessExecutedInBackground(shellScriptArgs, shellScriptEnvs,
      {
        val valueBytes = ConfluentUtils.serializeContainerToBytesArray(NuKafkaRuntimeTestSamples.avroPingRecord, inputSchemaId)
        kafkaClient.sendRawMessage(fixture.inputTopic, "fooKey".getBytes, valueBytes).futureValue
      },
      {
        val messages = kafkaClient.createConsumer().consume(fixture.outputTopic, secondsToWait = 60).take(1)
          .map(rec => ConfluentUtils.deserializeSchemaIdAndData[GenericRecord](rec.message(), NuKafkaRuntimeTestSamples.avroPingSchema)).toList
        messages shouldBe List((outputSchemaId, NuKafkaRuntimeTestSamples.avroPingRecord))
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

  private def registerSchemas(): Unit = {
    val schemaRegistryClient = new CachedSchemaRegistryClient(mappedSchemaRegistryAddress, 10)
    val parsedAvroSchema = ConfluentUtils.convertToAvroSchema(NuKafkaRuntimeTestSamples.avroPingSchema)
    inputSchemaId = schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.inputTopic), parsedAvroSchema)
    outputSchemaId = schemaRegistryClient.register(ConfluentUtils.valueSubject(fixture.outputTopic), parsedAvroSchema)
  }

  private def shellScriptPath: Path = {
    val targetItClassesDir = Path.of(getClass.getResource("/").toURI)
    val liteKafkaModuleDir = targetItClassesDir.getParent.getParent.getParent.getParent
    val stageDir = liteKafkaModuleDir.resolve("runtime/target/universal/stage")
    stageDir.resolve("bin/run.sh")
  }

}
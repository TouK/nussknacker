package pl.touk.nussknacker.engine.baseengine.kafka

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, GenericContainer}
import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.engine.spel.Implicits._


import java.io.File
import java.nio.charset.StandardCharsets

class NuKafkaEngineDockerTest extends FunSuite with ForAllTestContainer with KafkaSpec  with VeryPatientScalaFutures with Matchers with BeforeAndAfter {

  private val nuEngineRuntimeDockerName = "touk/nussknacker-standalone-engine:1.0.1-SNAPSHOT"
  private val dockerPort = 8080

  private val processId = "runTransactionSimpleScenarioViaDocker"
  private val inputTopic = s"input-$processId"
  private val outputTopic = s"output-$processId"

  override val container: Container = GenericContainer(nuEngineRuntimeDockerName, exposedPorts = Seq(dockerPort))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient.createTopic(inputTopic)
    kafkaClient.createTopic(outputTopic, 1)

    val scenario = EspProcessBuilder
      .id("test")
      .exceptionHandler()
      .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")
    val jsonFile = saveScenarioToTmp(scenario)
  }

  test("simple test run") {
    true shouldBe true
  }


  private def saveScenarioToTmp(scenario: EspProcess): File = {
    val canonicalScenario = ProcessCanonizer.canonize(scenario)
    val json = ProcessMarshaller.toJson(canonicalScenario)
    val jsonFile = File.createTempFile(getClass.getSimpleName, ".json")
    jsonFile.deleteOnExit()
    FileUtils.write(jsonFile, json.toString(), StandardCharsets.UTF_8)
    jsonFile
  }

}
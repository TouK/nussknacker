package pl.touk.nussknacker.engine.baseengine.kafka

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, GenericContainer}
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class NuKafkaEngineDockerTest extends FunSuite with ForAllTestContainer with KafkaSpec  with VeryPatientScalaFutures with Matchers with BeforeAndAfter {

  private val nuEngineRuntimeDockerName = "touk/nussknacker-standalone-engine:1.0.1-SNAPSHOT"
  private val dockerPort = 8080

  private val processId = "runTransactionSimpleScenarioViaDocker"
  private val inputTopic = s"input-$processId"
  private val outputTopic = s"output-$processId"

  override val container: Container =
    GenericContainer(
      nuEngineRuntimeDockerName,
      exposedPorts = Seq(dockerPort),
      classpathResourceMapping = Seq((
        "scenario.json",
        "/opt/nussknacker/conf/scenario.json",
        BindMode.READ_ONLY))
    )

//  override protected def beforeAll(): Unit = {
//    super.beforeAll()
//    kafkaClient.createTopic(inputTopic)
//    kafkaClient.createTopic(outputTopic, 1)
//
//  }

  test("simple test run") {

//    val engineExitCodePromise = Promise[Int]
//
//    val thread = new Thread(() => {
//      engineExitCodePromise.complete(Try {
//        val process =
//        logger.info(s"Started engine process with pid: ${process.pid()}")
//        StreamUtils.copy(process.getInputStream, System.out)
//        StreamUtils.copy(process.getErrorStream, System.err)
//        process.waitFor()
//        process.exitValue()
//      })
//    })
//
//    try {
//      thread.start()
//
//      val input = """{"foo": "ping"}"""
//      kafkaClient.sendMessage(inputTopic, input).futureValue
//
//      //      val messages = kafkaClient.createConsumer().consume(outputTopic).take(1).map(rec => new String(rec.message()))
//      //      messages shouldBe List(input)
//    } finally {
//      //      thread.interrupt()
//    }
//
//    engineExitCodePromise.future.futureValue shouldEqual 0 // success exit code

  }

}
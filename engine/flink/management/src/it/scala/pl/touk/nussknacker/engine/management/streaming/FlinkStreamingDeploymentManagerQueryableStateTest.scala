package pl.touk.nussknacker.engine.management.streaming

import org.apache.flink.api.scala._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.definition.SignalDispatcher
import pl.touk.nussknacker.engine.management.{FlinkQueryableClient, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.engine.spel.Implicits._

import scala.concurrent.ExecutionContext.Implicits._

class FlinkStreamingDeploymentManagerQueryableStateTest extends FunSuite with Matchers with StreamingDockerTest {

  //see DevConfigCreator
  val oneElementValue = "One element"

  override protected def classPath: List[String] = ClassPaths.scalaClasspath

  test("fetch queryable state for all keys") {
    kafkaClient.createTopic("esp.signals")

    val lockProcess = EspProcessBuilder
      .id("queryableStateProc1")
      .parallelism(1)
      .source("start", "oneSource")
      .customNode("lock", "lockOutput", "lockStreamTransformer", "input" -> "#input")
      .emptySink("sink", "monitor")

    val version = ProcessVersion(VersionId.initialVersionId, ProcessName(lockProcess.id), ProcessId(1), "user1", None)

    deployProcessAndWaitIfRunning(lockProcess, version)
    val jobId = deploymentManager.findJobStatus(version.processName).futureValue
      .flatMap(_.deploymentId).get.value

    val processingTypeConfig = ProcessingTypeConfig.read(configWithHostKafka)
    val client = new FlinkStreamingDeploymentManagerProvider()
      .createQueryableClient(processingTypeConfig.deploymentConfig).get.asInstanceOf[FlinkQueryableClient]

    def queryState(): Boolean = client.fetchState[java.lang.Boolean](
      jobId = jobId,
      queryName = "single-lock-state",
      key = oneElementValue).map(Boolean.box(_)).futureValue

    eventually {
      queryState() shouldBe true
    }

    //see RemoveLockProcessSignalFactory for details
    SignalDispatcher
      .dispatchSignal(processingTypeConfig.toModelData)("removeLockSignal",
        lockProcess.id, Map("lockId" -> oneElementValue))

    eventually {
      queryState() shouldBe false
    }
  }

}

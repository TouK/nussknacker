package pl.touk.nussknacker.engine.management

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.runtime.jobgraph.JobStatus
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.BatchProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

class BatchFlinkProcessManagerSpec extends FunSuite with Matchers with ScalaFutures with Eventually with DockerTest {

  import pl.touk.nussknacker.engine.spel.Implicits._

  import scala.concurrent.duration._

  test("deploy process in running flink") {
    val processName = ProcessName("batchProcess")

    val version = ProcessVersion(versionId = 15, processName = processName, user = "user1", modelVersion = Some(13))
    val process = prepareProcess(processName)

    deployProcessAndWaitUntilFinished(process, version)

    processVersion(processName) shouldBe Some(version)
  }

  private def prepareProcess(processName: ProcessName): EspProcess = {
    BatchProcessBuilder
      .id(processName.value)
      .exceptionHandler("param1" -> "'val1'")
      .source("source", "batch-elements-source", "elements" -> "{1, 2, 3, 4, 5, 6}")
      .filter("filter", "#input % 2 == 0")
      .sink("sink", "#input", "batch-file-sink", "path" -> "'/tmp/batchTestOutput'")
  }

  private def deployProcessAndWaitUntilFinished(process: EspProcess, processVersion: ProcessVersion): Unit = {
    val marshaled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
    assert(batchProcessManager.deploy(processVersion, GraphProcess(marshaled), savepointPath = None).isReadyWithin(100 seconds))
    eventually {
      val jobStatus = batchProcessManager.findJobStatus(ProcessName(process.id)).futureValue
      jobStatus.map(_.status) shouldBe Some(JobStatus.FINISHED.name())
    }
  }

  private def processVersion(processName: ProcessName): Option[ProcessVersion] =
    batchProcessManager.findJobStatus(processName).futureValue.flatMap(_.version)

  private def batchConfig : Config = ConfigFactory.load()
    .withValue("flinkConfig.restUrl", fromAnyRef(s"http://${jobManagerContainer.getIpAddresses().futureValue.head}:$FlinkJobManagerRestPort"))

  private lazy val batchProcessManager = {
    val typeConfig = BatchFlinkProcessManagerProvider.defaultTypeConfig(batchConfig)
    new BatchFlinkProcessManagerProvider().createProcessManager(typeConfig.toModelData, typeConfig.engineConfig)
  }
}

package pl.touk.nussknacker.engine.management.batch

import java.nio.file.Files

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.BatchProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.FlinkStateStatus
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

class FlinkBatchProcessManagerSpec extends FunSuite with Matchers with BatchDockerTest {
  import pl.touk.nussknacker.engine.spel.Implicits._
  import scala.collection.JavaConverters._
  import scala.concurrent.duration._

  private lazy val testOutputPath = testDir.resolve("testOutput")

  test("deploy process in running flink") {
    val processName = ProcessName("batchProcess")
    val version = ProcessVersion(versionId = 15, processName = processName, user = "user1", modelVersion = Some(13))
    val process = prepareProcess(processName)

    deployProcessAndWaitUntilFinished(process, version)

    processVersion(processName) shouldBe Some(version)
    Files.readAllLines(testOutputPath) shouldBe List("2", "4", "6").asJava
  }

  private def prepareProcess(processName: ProcessName): EspProcess = {
    BatchProcessBuilder
      .id(processName.value)
      .exceptionHandler()
      .source("source", "elements-source", "elements" -> "{1, 2, 3, 4, 5, 6}")
      .filter("filter", "#input % 2 == 0")
      .sink("sink", "#input", "file-sink", "path" -> s"'$testOutputPath'")
  }

  private def deployProcessAndWaitUntilFinished(process: EspProcess, processVersion: ProcessVersion): Unit = {
    val marshaled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
    assert(processManager.deploy(processVersion, GraphProcess(marshaled), savepointPath = None).isReadyWithin(100 seconds))
    eventually {
      val jobStatus = processManager.findJobStatus(ProcessName(process.id)).futureValue
      jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Finished.name)
      jobStatus.map(_.status.isFinished) shouldBe Some(true)
    }
  }

  private def processVersion(processName: ProcessName): Option[ProcessVersion] =
    processManager.findJobStatus(processName).futureValue.flatMap(_.version)
}

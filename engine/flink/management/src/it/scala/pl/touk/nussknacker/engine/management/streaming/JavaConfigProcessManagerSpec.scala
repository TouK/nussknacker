package pl.touk.nussknacker.engine.management.streaming

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.FlinkStateStatus
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

import scala.concurrent.duration._

class JavaConfigProcessManagerSpec extends FunSuite with Matchers with StreamingDockerTest {

  override protected def classPath: String = s"./engine/flink/management/java_sample/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/managementJavaSample.jar"

  test("deploy java process in running flink") {
    val processId = "runningJavaFlink"

    val process = EspProcessBuilder
          .id(processId)
          .exceptionHandler()
          .source("startProcess", "source")
          .emptySink("endSend", "sink")

    val marshaled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
    assert(processManager.deploy(ProcessVersion.empty.copy(processName=ProcessName(process.id)), GraphProcess(marshaled), None).isReadyWithin(100 seconds))
    Thread.sleep(1000)
    val jobStatus = processManager.findJobStatus(ProcessName(process.id)).futureValue
    jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
    jobStatus.map(_.status.isRunning) shouldBe Some(true)

    assert(processManager.cancel(ProcessName(process.id)).isReadyWithin(10 seconds))
  }
}

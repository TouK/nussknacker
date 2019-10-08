package pl.touk.nussknacker.engine.management

import java.util.Collections

import com.typesafe.config.{Config, ConfigValueFactory}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.concurrent.duration._

class JavaConfigProcessManagerSpec extends FunSuite with Matchers with ScalaFutures with Eventually with DockerTest {


  override def config: Config = {
    super.config
      .withValue("flinkConfig.classpath",
        ConfigValueFactory.fromIterable(Collections.singletonList("./engine/flink/management/java_sample/target/scala-2.11/managementJavaSample.jar")))
  }

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
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    assert(processManager.cancel(ProcessName(process.id)).isReadyWithin(10 seconds))
  }




}

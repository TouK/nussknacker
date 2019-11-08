package pl.touk.nussknacker.engine.management.batch

import java.nio.file.Path

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.whisk.docker.{DockerContainer, VolumeMapping}
import org.scalatest.Suite
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.management.{DockerTest, FlinkBatchProcessManagerProvider}

import scala.collection.JavaConverters._

trait BatchDockerTest extends DockerTest { self: Suite =>

  lazy val testDir: Path = prepareVolumeDir()

  lazy val taskManagerContainer: DockerContainer = buildTaskManagerContainer(volumes = List(VolumeMapping(testDir.toString, testDir.toString, rw = true)))

  abstract override def dockerContainers: List[DockerContainer] =
    List(
      zookeeperContainer,
      jobManagerContainer,
      taskManagerContainer
    ) ++ super.dockerContainers

  def config: Config = ConfigFactory.load()
    .withValue("flinkConfig.restUrl", fromAnyRef(s"http://${jobManagerContainer.getIpAddresses().futureValue.head}:$FlinkJobManagerRestPort"))
    .withValue("flinkConfig.classpath", ConfigValueFactory.fromIterable(List("./engine/flink/management/batch_sample/target/scala-2.11/managementBatchSample.jar").asJava))

  lazy val processManager: ProcessManager = {
    val typeConfig = FlinkBatchProcessManagerProvider.defaultTypeConfig(config)
    new FlinkBatchProcessManagerProvider().createProcessManager(typeConfig.toModelData, typeConfig.engineConfig)
  }
}

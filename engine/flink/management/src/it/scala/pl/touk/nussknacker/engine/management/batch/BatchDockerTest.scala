package pl.touk.nussknacker.engine.management.batch

import java.nio.file.Path

import com.whisk.docker.{DockerContainer, VolumeMapping}
import org.scalatest.Suite
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.management.{DockerTest, FlinkBatchProcessManagerProvider}
import pl.touk.nussknacker.engine.util.config.ScalaBinaryConfig

trait BatchDockerTest extends DockerTest { self: Suite =>

  lazy val testDir: Path = prepareVolumeDir()

  lazy val taskManagerContainer: DockerContainer = buildTaskManagerContainer(volumes = List(VolumeMapping(testDir.toString, testDir.toString, rw = true)))

  abstract override def dockerContainers: List[DockerContainer] =
    List(
      zookeeperContainer,
      jobManagerContainer,
      taskManagerContainer
    ) ++ super.dockerContainers

  lazy val processManager: ProcessManager = {
    val typeConfig = FlinkBatchProcessManagerProvider.defaultTypeConfig(config)
    new FlinkBatchProcessManagerProvider().createProcessManager(typeConfig.toModelData, typeConfig.engineConfig)
  }

  override protected def classPath: String
    = s"./engine/flink/management/batch_sample/target/scala-${ScalaBinaryConfig.scalaBinaryVersion}/managementBatchSample.jar"
}

package pl.touk.nussknacker.engine.management.batch

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.Suite
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.management.{DockerTest, FlinkBatchProcessManagerProvider}

import scala.collection.JavaConverters._

trait BatchDockerTest extends DockerTest { self: Suite =>

  def config: Config = ConfigFactory.load()
    .withValue("flinkConfig.restUrl", fromAnyRef(s"http://${jobManagerContainer.getIpAddresses().futureValue.head}:$FlinkJobManagerRestPort"))
    .withValue("flinkConfig.classpath", ConfigValueFactory.fromIterable(List("./engine/flink/management/batch_sample/target/scala-2.11/managementBatchSample.jar").asJava))

  lazy val processManager: ProcessManager = {
    val typeConfig = FlinkBatchProcessManagerProvider.defaultTypeConfig(config)
    new FlinkBatchProcessManagerProvider().createProcessManager(typeConfig.toModelData, typeConfig.engineConfig)
  }
}

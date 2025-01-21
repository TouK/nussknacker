package pl.touk.nussknacker.engine.lite.utils

import com.dimafeng.testcontainers.GenericContainer
import org.slf4j.Logger
import org.testcontainers.containers.output.{OutputFrame, Slf4jLogConsumer}
import org.testcontainers.containers.wait.strategy.{Wait, WaitStrategy, WaitStrategyTarget}
import org.testcontainers.containers.{BindMode, Network}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.engine.version.BuildInfo

import java.io.File
import java.time.Duration
import java.util.function.Consumer
import scala.jdk.CollectionConverters._

object NuRuntimeDockerTestUtils {

  private val dockerTag: String =
    sys.env.getOrElse("dockerTagName", s"${BuildInfo.version}_scala-${ScalaMajorVersionConfig.scalaMajorVersion}")
  private val liteKafkaRuntimeDockerName = s"touk/nussknacker-lite-runtime-app:$dockerTag"

  val runtimeApiPort = 8080

  def startRuntimeContainer(
      scenarioFile: File,
      logger: Logger,
      networkOpt: Option[Network] = None,
      checkReady: Boolean = true,
      additionalEnvs: Map[String, String] = Map.empty
  ): GenericContainer = {
    val runtimeContainer = GenericContainer(
      liteKafkaRuntimeDockerName,
      exposedPorts = Seq(runtimeApiPort),
      env = sys.env.get("NUSSKNACKER_LOG_LEVEL").map("NUSSKNACKER_LOG_LEVEL" -> _).toMap ++ additionalEnvs
    )
    networkOpt.foreach(runtimeContainer.underlyingUnsafeContainer.withNetwork)
    runtimeContainer.underlyingUnsafeContainer.withFileSystemBind(
      scenarioFile.toString,
      "/opt/nussknacker/conf/scenario.json",
      BindMode.READ_ONLY
    )
    runtimeContainer.underlyingUnsafeContainer.withFileSystemBind(
      NuRuntimeTestUtils.deploymentDataFile.toString,
      "/opt/nussknacker/conf/deploymentConfig.conf",
      BindMode.READ_ONLY
    )
    val waitStrategy = if (checkReady) Wait.forHttp("/ready").forPort(runtimeApiPort) else DumbWaitStrategy
    runtimeContainer.underlyingUnsafeContainer.setWaitStrategy(waitStrategy)
    val logConsumer: Consumer[OutputFrame] = new Slf4jLogConsumer(logger)
    runtimeContainer.underlyingUnsafeContainer.setLogConsumers((logConsumer :: Nil).asJava)
    runtimeContainer.start()
    runtimeContainer
  }

  private object DumbWaitStrategy extends WaitStrategy {
    override def waitUntilReady(waitStrategyTarget: WaitStrategyTarget): Unit = {}
    override def withStartupTimeout(startupTimeout: Duration): WaitStrategy   = this
  }

}

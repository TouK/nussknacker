package pl.touk.nussknacker.engine.embedded

import cats.data.Validated.valid
import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.{
  BaseModelDataProvider,
  CustomProcessValidator,
  DeploymentManagerDependencies,
  JobsRecoverySettings
}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.embedded.requestresponse.RequestResponseDeploymentStrategy
import pl.touk.nussknacker.engine.embedded.streaming.StreamingDeploymentStrategy
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.{DropwizardMetricsProviderFactory, LiteMetricRegistryFactory}
import pl.touk.nussknacker.lite.manager.LiteDeploymentManagerProvider

import java.time.Clock
import scala.concurrent.duration.FiniteDuration

class EmbeddedDeploymentManagerProvider extends LiteDeploymentManagerProvider {

  override val name: String = "lite-embedded"

  private val clock: Clock = Clock.systemUTC()

  override def createDeploymentManager(
      modelDataProvider: BaseModelDataProvider,
      dependencies: DeploymentManagerDependencies,
      engineConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    import dependencies._
    val strategy = forMode(engineConfig)(
      new StreamingDeploymentStrategy,
      RequestResponseDeploymentStrategy(engineConfig, clock)
    )

    val metricRegistry = LiteMetricRegistryFactory
      .usingHostnameAsDefaultInstanceId(modelDataProvider.getCurrentModelData().namingStrategy.namespace)
      .prepareRegistry(engineConfig)
    val contextPreparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

    strategy.open(modelDataProvider, contextPreparer)
    valid(new EmbeddedDeploymentManager(modelDataProvider, strategy))
  }

  override protected def defaultRequestResponseSlug(scenarioName: ProcessName, config: Config): String =
    RequestResponseDeploymentStrategy.defaultSlug(scenarioName)

  override def additionalValidators(config: Config): List[CustomProcessValidator] = forMode(config)(
    Nil,
    List(EmbeddedRequestResponseScenarioValidator)
  )

  override def jobsRecoverySettings(config: Config): JobsRecoverySettings =
    JobsRecoverySettings(recoverJobsOnStart = true)

}

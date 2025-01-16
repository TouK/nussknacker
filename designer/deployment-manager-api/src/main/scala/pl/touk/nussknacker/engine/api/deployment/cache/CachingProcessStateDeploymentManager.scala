package pl.touk.nussknacker.engine.api.deployment.cache

import com.github.benmanes.caffeine.cache.{AsyncCache, Caffeine}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._

class CachingProcessStateDeploymentManager(
    delegate: DeploymentManager,
    cacheTTL: FiniteDuration,
    override val deploymentSynchronisationSupport: DeploymentSynchronisationSupport,
    override val stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport,
    override val schedulingSupport: SchedulingSupport,
) extends DeploymentManager {

  private val cache: AsyncCache[ProcessName, List[StatusDetails]] = Caffeine
    .newBuilder()
    .expireAfterWrite(java.time.Duration.ofMillis(cacheTTL.toMillis))
    .buildAsync[ProcessName, List[StatusDetails]]

  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction],
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
      currentlyPresentedVersionId: Option[VersionId],
  ): Future[ProcessState] =
    delegate.resolve(
      idWithName,
      statusDetails,
      lastStateAction,
      latestVersionId,
      deployedVersionId,
      currentlyPresentedVersionId
    )

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    def fetchAndUpdateCache(): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
      val resultFuture = delegate.getProcessStates(name)
      cache.put(name, resultFuture.map(_.value).toJava.toCompletableFuture)
      resultFuture
    }

    freshnessPolicy match {
      case DataFreshnessPolicy.Fresh =>
        fetchAndUpdateCache()
      case DataFreshnessPolicy.CanBeCached =>
        Option(cache.getIfPresent(name))
          .map(_.toScala.map(WithDataFreshnessStatus.cached))
          .getOrElse(
            fetchAndUpdateCache()
          ) // Data fetched from the delegate can also be cached, e.g. Flink's Cached API client.
    }
  }

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] =
    delegate.processCommand(command)

  override def processStateDefinitionManager: ProcessStateDefinitionManager = delegate.processStateDefinitionManager

  override def close(): Unit = delegate.close()

}

object CachingProcessStateDeploymentManager extends LazyLogging {

  def wrapWithCachingIfNeeded(
      delegate: DeploymentManager,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): DeploymentManager = {
    scenarioStateCacheTTL
      .map { cacheTTL =>
        logger.debug(s"Wrapping DeploymentManager: $delegate with caching mechanism with TTL: $cacheTTL")
        new CachingProcessStateDeploymentManager(
          delegate,
          cacheTTL,
          delegate.deploymentSynchronisationSupport,
          delegate.stateQueryForAllScenariosSupport,
          delegate.schedulingSupport,
        )
      }
      .getOrElse {
        logger.debug(s"Skipping ProcessState caching for DeploymentManager: $delegate")
        delegate
      }
  }

}

final case class ScenarioStateCachingConfig(enabled: Boolean, cacheTTL: Option[FiniteDuration])

object ScenarioStateCachingConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  private val ScenarioStateCachingConfigKey = "scenarioStateCaching"

  val Default: ScenarioStateCachingConfig = ScenarioStateCachingConfig(enabled = true, Some(10 seconds))

  def extractScenarioStateCacheTTL(config: Config): Option[FiniteDuration] = {
    val cachingConfig = config.getAs[ScenarioStateCachingConfig](ScenarioStateCachingConfigKey).getOrElse(Default)

    cachingConfig match {
      case ScenarioStateCachingConfig(true, None) =>
        throw new IllegalArgumentException(
          s"Invalid config: $this. If you want to enable $ScenarioStateCachingConfigKey, you have to define cacheTTL."
        )
      case config => config.cacheTTL
    }
  }

}

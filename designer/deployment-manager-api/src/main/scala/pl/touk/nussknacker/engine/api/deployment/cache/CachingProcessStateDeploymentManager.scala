package pl.touk.nussknacker.engine.api.deployment.cache

import com.github.benmanes.caffeine.cache.{AsyncCache, Caffeine}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._

class CachingProcessStateDeploymentManager(delegate: DeploymentManager, cacheTTL: FiniteDuration)
    extends DeploymentManager {

  private val cache: AsyncCache[ProcessName, List[StatusDetails]] = Caffeine
    .newBuilder()
    .expireAfterWrite(java.time.Duration.ofMillis(cacheTTL.toMillis))
    .buildAsync[ProcessName, List[StatusDetails]]

  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction]
  ): Future[ProcessState] =
    delegate.resolve(idWithName, statusDetails, lastStateAction)

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    freshnessPolicy match {
      case DataFreshnessPolicy.Fresh =>
        val resultFuture = delegate.getProcessStates(name)
        cache.put(name, resultFuture.map(_.value).toJava.toCompletableFuture)
        resultFuture
      case DataFreshnessPolicy.CanBeCached =>
        Option(cache.getIfPresent(name))
          .map(_.toScala.map(WithDataFreshnessStatus(_, cached = true)))
          .getOrElse {
            cache
              .get(name, (_, _) => delegate.getProcessStates(name).map(_.value).toJava.toCompletableFuture)
              .toScala
              .map(WithDataFreshnessStatus(_, cached = false))
          }
    }
  }

  override def validate(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): Future[Unit] =
    delegate.validate(processVersion, deploymentData, canonicalProcess)

  override def deploy(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] =
    delegate.deploy(processVersion, deploymentData, canonicalProcess, savepointPath)

  override def cancel(name: ProcessName, user: User): Future[Unit] =
    delegate.cancel(name, user)

  override def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] =
    delegate.cancel(name, deploymentId, user)

  override def test(
      name: ProcessName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData
  ): Future[TestProcess.TestResults] =
    delegate.test(name, canonicalProcess, scenarioTestData)

  override def processStateDefinitionManager: ProcessStateDefinitionManager = delegate.processStateDefinitionManager

  override def customActions: List[CustomAction] = delegate.customActions

  override def invokeCustomAction(
      actionRequest: CustomActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[Either[CustomActionError, CustomActionResult]] =
    delegate.invokeCustomAction(actionRequest, canonicalProcess)

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] =
    delegate.savepoint(name, savepointDir)

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] =
    delegate.stop(name, savepointDir, user)

  override def stop(
      name: ProcessName,
      deploymentId: DeploymentId,
      savepointDir: Option[String],
      user: User
  ): Future[SavepointResult] =
    delegate.stop(name, deploymentId, savepointDir, user)

  override def close(): Unit = delegate.close()

}

object CachingProcessStateDeploymentManager extends LazyLogging {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  val scenarioStateCachingConfigKey = "scenarioStateCaching"

  def wrapWithCachingIfNeeded(delegate: DeploymentManager, config: Config): DeploymentManager = {
    val cachingConfig =
      config.getAs[ScenarioStateCachingConfig](scenarioStateCachingConfigKey).getOrElse(ScenarioStateCachingConfig())
    if (cachingConfig.enabled) {
      val cacheTTL = cachingConfig.cacheTTL
        .getOrElse(
          throw new IllegalArgumentException(
            s"Invalid config: $this. If you want to enable processStateCaching, you have to define cacheTTL"
          )
        )
      logger.debug(s"Wrapping DeploymentManager: $delegate with caching mechanism with TTL: $cacheTTL")
      delegate match {
        case postprocessing: PostprocessingProcessStatus =>
          new CachingProcessStateDeploymentManager(delegate, cacheTTL) with PostprocessingProcessStatus {
            override def postprocess(
                idWithName: ProcessIdWithName,
                statusDetailsList: List[StatusDetails]
            ): Future[Option[ProcessAction]] =
              postprocessing.postprocess(idWithName, statusDetailsList)
          }
        case _ =>
          new CachingProcessStateDeploymentManager(delegate, cacheTTL)
      }
    } else {
      logger.debug(s"Skipping ProcessState caching for DeploymentManager: $delegate")
      delegate
    }
  }

}

final case class ScenarioStateCachingConfig(
    enabled: Boolean = true,
    cacheTTL: Option[FiniteDuration] = Some(10 seconds)
)

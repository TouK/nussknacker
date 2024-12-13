package pl.touk.nussknacker.engine.management.periodic.db

import cats.Monad
import com.github.benmanes.caffeine.cache.{AsyncCache, Caffeine}
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessActionId}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic._
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData.WithCanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model._

import java.time.LocalDateTime
import scala.compat.java8.FutureConverters.{CompletionStageOps, FutureOps}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class PeriodicProcessesRepositoryCachingDecorator(
    val underlying: PeriodicProcessesRepository,
    cacheTTL: FiniteDuration,
)(implicit ec: ExecutionContext)
    extends PeriodicProcessesRepository {

  private val activeSchedulesCache: AsyncCache[Int, Map[ProcessName, SchedulesState]] =
    Caffeine
      .newBuilder()
      .asInstanceOf[Caffeine[Int, Map[ProcessName, SchedulesState]]]
      .expireAfterWrite(java.time.Duration.ofMillis(cacheTTL.toMillis))
      .buildAsync[Int, Map[ProcessName, SchedulesState]]

  private val inactiveSchedulesCache: AsyncCache[(Int, Int), Map[ProcessName, SchedulesState]] =
    Caffeine
      .newBuilder()
      .asInstanceOf[Caffeine[(Int, Int), Map[ProcessName, SchedulesState]]]
      .expireAfterWrite(java.time.Duration.ofMillis(cacheTTL.toMillis))
      .buildAsync[(Int, Int), Map[ProcessName, SchedulesState]]

  type Action[T] = underlying.Action[T]

  override implicit def monad: Monad[Action] = underlying.monad

  override def run[T](action: Action[T]): Future[T] = underlying.run(action)

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[SchedulesState] = {
    fetchActiveSchedules(deploymentsPerScheduleMaxCount)
      .map(_.getOrElse(processName, SchedulesState(Map.empty)))
  }

  override def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[Map[ProcessName, SchedulesState]] = {
    fetchActiveSchedules(deploymentsPerScheduleMaxCount)
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[SchedulesState] = {
    fetchInactiveSchedules(inactiveProcessesMaxCount, deploymentsPerScheduleMaxCount)
      .map(_.getOrElse(processName, SchedulesState(Map.empty)))
  }

  override def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[Map[ProcessName, SchedulesState]] = {
    fetchInactiveSchedules(inactiveProcessesMaxCount, deploymentsPerScheduleMaxCount)
  }

  private def fetchActiveSchedules(deploymentsPerScheduleMaxCount: Int)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[Map[ProcessName, SchedulesState]] =
    fetchUsingCache[Int](
      cache = activeSchedulesCache,
      key = deploymentsPerScheduleMaxCount,
      fetch = deploymentsPerScheduleMaxCount =>
        underlying.getLatestDeploymentsForActiveSchedules(deploymentsPerScheduleMaxCount)
    )

  private def fetchInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[Map[ProcessName, SchedulesState]] =
    fetchUsingCache[(Int, Int)](
      cache = inactiveSchedulesCache,
      key = (inactiveProcessesMaxCount, deploymentsPerScheduleMaxCount),
      fetch = key =>
        underlying.getLatestDeploymentsForLatestInactiveSchedules(
          inactiveProcessesMaxCount = key._1,
          deploymentsPerScheduleMaxCount = key._2
        )
    )

  private def fetchUsingCache[KEY](
      cache: AsyncCache[KEY, Map[ProcessName, SchedulesState]],
      key: KEY,
      fetch: KEY => Future[Map[ProcessName, SchedulesState]],
  )(implicit freshnessPolicy: DataFreshnessPolicy) = {
    def fetchAndUpdateCache(): Future[Map[ProcessName, SchedulesState]] = {
      val resultFuture = fetch(key)
      cache.put(key, resultFuture.toJava.toCompletableFuture)
      resultFuture
    }
    freshnessPolicy match {
      case DataFreshnessPolicy.Fresh =>
        fetchAndUpdateCache()
      case DataFreshnessPolicy.CanBeCached =>
        Option(cache.getIfPresent(key))
          .map(_.toScala)
          .getOrElse(fetchAndUpdateCache())
    }

    Option(cache.getIfPresent(key))
      .map(_.toScala)
      .getOrElse(fetchAndUpdateCache())
  }

  // Methods below are not using caching mechanism

  override def getSchedulesState(
      scenarioName: ProcessName,
      after: Option[LocalDateTime]
  ): Action[SchedulesState] =
    underlying.getSchedulesState(scenarioName, after)

  override def create(
      deploymentWithJarData: WithCanonicalProcess,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId
  ): Action[PeriodicProcess[WithCanonicalProcess]] =
    underlying.create(deploymentWithJarData, scheduleProperty, processActionId)

  override def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int
  ): Action[PeriodicProcessDeployment[WithCanonicalProcess]] =
    underlying.schedule(id, scheduleName, runAt, deployMaxRetries)

  override def findToBeDeployed: Action[Seq[PeriodicProcessDeployment[WithCanonicalProcess]]] =
    underlying.findToBeDeployed

  override def findToBeRetried: Action[Seq[PeriodicProcessDeployment[WithCanonicalProcess]]] =
    underlying.findToBeRetried

  override def findProcessData(
      id: PeriodicProcessDeploymentId
  ): Action[PeriodicProcessDeployment[WithCanonicalProcess]] =
    underlying.findProcessData(id)

  override def findProcessData(processName: ProcessName): Action[Seq[PeriodicProcess[WithCanonicalProcess]]] =
    underlying.findProcessData(processName)

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus]
  ): Action[SchedulesState] =
    underlying.findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(expectedDeploymentStatuses)

  override def markDeployed(id: PeriodicProcessDeploymentId): Action[Unit] =
    underlying.markDeployed(id)

  override def markFailed(id: PeriodicProcessDeploymentId): Action[Unit] =
    underlying.markFailed(id)

  override def markFinished(id: PeriodicProcessDeploymentId): Action[Unit] =
    underlying.markFinished(id)

  override def markFailedOnDeployWithStatus(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus,
      retriesLeft: Int,
      retryAt: Option[LocalDateTime]
  ): Action[Unit] =
    underlying.markFailedOnDeployWithStatus(id, status, retriesLeft, retryAt)

  override def markInactive(processId: PeriodicProcessId): Action[Unit] =
    underlying.markInactive(processId)

}

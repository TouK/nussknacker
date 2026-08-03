package pl.touk.nussknacker.ui.factory

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import pl.touk.nussknacker.engine.util.SLF4JBridgeHandlerRegistrar
import pl.touk.nussknacker.ui.config.{DesignerConfig, DesignerConfigLoader}
import pl.touk.nussknacker.ui.metrics.RepositoryGauges
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.server.{NussknackerHttpServer, PekkoHttpBasedRouteFactory}

import java.time.Clock
import scala.concurrent.Future

class NussknackerAppFactory(
    designerConfigLoader: DesignerConfigLoader,
) extends LazyLogging {

  def createApp(clock: Clock = Clock.systemUTC()): Resource[IO, Unit] = {
    for {
      _ <- Resource.eval(IO(SLF4JBridgeHandlerRegistrar.register()))

      alreadyLoadedConfig <- Resource.eval(designerConfigLoader.loadDesignerConfig())

      infrastructureServices <- InfrastructureServices.create(clock, alreadyLoadedConfig)
      domainServices         <- DomainServices.create(designerConfigLoader, alreadyLoadedConfig, infrastructureServices)
      _ = initMetrics(
        infrastructureServices.metricsRegistry,
        alreadyLoadedConfig,
        domainServices.futureProcessRepository
      )

      route <- PekkoHttpBasedRouteFactory.createRoute(
        designerConfig = alreadyLoadedConfig,
        infrastructureServices = infrastructureServices,
        domainServices = domainServices
      )
      _ <- Resource.eval(registerDeploymentRecovery(domainServices, infrastructureServices))
      _ <- domainServices.leadership.startHeartbeat()
      _ <- new NussknackerHttpServer(infrastructureServices, alreadyLoadedConfig).start(route)
      _ <- startJmxReporter(infrastructureServices.metricsRegistry)
      _ <- createStartAndStopLoggingEntries()
    } yield ()
  }

  /** Recovery is triggered each time this node acquires leadership rather than once at startup.
    * A node that starts as a non-leader (another node holds the lease) would otherwise never
    * run recovery even after winning leadership following a failover.
    */
  private def registerDeploymentRecovery(
      domainServices: DomainServices,
      infrastructureServices: InfrastructureServices,
  ): IO[Unit] =
    domainServices.leadership.onLeadershipAcquired(
      IO.fromFuture(
        IO(
          domainServices.reconciler
            .recoverNotRunningDeploymentsThatShouldBeRunning(
              shouldRecover = _.recoverJobsOnStart,
              isLeader = () => domainServices.leadership.isLeader()
            )
            .recover { case exception => logger.error("Error while deployments recovery", exception) }(
              infrastructureServices.executionContextWithIORuntime
            )
        )
      )
    )

  private def startJmxReporter(metricsRegistry: MetricRegistry) = {
    Resource.eval(IO(JmxReporter.forRegistry(metricsRegistry).build().start()))
  }

  private def createStartAndStopLoggingEntries() = {
    Resource
      .make(
        acquire = IO(logger.info("Nussknacker started!"))
      )(
        release = _ => IO(logger.info("Stopping Nussknacker ..."))
      )
  }

  private def initMetrics(
      metricsRegistry: MetricRegistry,
      designerConfig: DesignerConfig,
      processRepository: FetchingProcessRepository[Future]
  ): Unit = {
    new RepositoryGauges(
      metricsRegistry,
      designerConfig.repositoryGaugesCacheDuration,
      processRepository
    )
      .prepareGauges()
  }

}

package pl.touk.nussknacker.ui.factory

import cats.effect.{IO, Resource}
import cats.implicits.catsSyntaxSemigroup
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, SLF4JBridgeHandlerRegistrar}
import pl.touk.nussknacker.ui.config.{DesignerConfig, DesignerConfigLoader}
import pl.touk.nussknacker.ui.metrics.RepositoryGauges
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.server.{
  CustomHttpServiceProvidersLoader,
  NussknackerHttpServer,
  PekkoHttpBasedRouteFactory
}
import pl.touk.nussknacker.ui.server.CustomHttpServiceProvidersLoader.CustomHttpServiceLoadingMode

import java.time.Clock
import scala.concurrent.Future

class NussknackerAppFactory(
    designerConfigLoader: DesignerConfigLoader,
) extends LazyLogging {

  def createApp(clock: Clock = Clock.systemUTC()): Resource[IO, Unit] = {
    for {
      _ <- Resource.eval(IO(JavaClassVersionChecker.check()))
      _ <- Resource.eval(IO(SLF4JBridgeHandlerRegistrar.register()))

      alreadyLoadedConfig <- Resource.eval(designerConfigLoader.loadDesignerConfig())
      customHttpServicesProvidersWithoutDependencies <-
        CustomHttpServiceProvidersLoader
          .loadCustomHttpServiceProviders(
            alreadyLoadedConfig,
            CustomHttpServiceLoadingMode.LoadServicesWithoutDependencies,
          )
      infrastructureServices <- InfrastructureServices.create(clock, alreadyLoadedConfig)
      customHttpServiceProvidersWithInfrastructureDependencies <-
        CustomHttpServiceProvidersLoader
          .loadCustomHttpServiceProviders(
            alreadyLoadedConfig,
            CustomHttpServiceLoadingMode.LoadServicesWithInfrastructureDependencies(infrastructureServices),
          )
      domainServices <- DomainServices.create(designerConfigLoader, alreadyLoadedConfig, infrastructureServices)
      customHttpServiceProvidersWithInfrastructureAndDomainDependencies <-
        CustomHttpServiceProvidersLoader
          .loadCustomHttpServiceProviders(
            alreadyLoadedConfig,
            CustomHttpServiceLoadingMode.LoadServicesWithInfrastructureAndDomainDependencies(
              infrastructureServices,
              domainServices,
            ),
          )
      _ = initMetrics(
        infrastructureServices.metricsRegistry,
        alreadyLoadedConfig,
        domainServices.futureProcessRepository
      )
      route <- PekkoHttpBasedRouteFactory.createRoute(
        designerConfig = alreadyLoadedConfig,
        infrastructureServices = infrastructureServices,
        domainServices = domainServices,
        customHttpServiceProviders = customHttpServicesProvidersWithoutDependencies |+|
          customHttpServiceProvidersWithInfrastructureDependencies |+|
          customHttpServiceProvidersWithInfrastructureAndDomainDependencies
      )
      _ <- new NussknackerHttpServer(infrastructureServices, alreadyLoadedConfig).start(route)
      _ <- startJmxReporter(infrastructureServices.metricsRegistry)
      _ <- createStartAndStopLoggingEntries()
    } yield ()
  }

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

package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import pl.touk.nussknacker.engine.util.{
  ExecutionContextWithIORuntimeAdapter,
  JavaClassVersionChecker,
  SLF4JBridgeHandlerRegistrar
}
import pl.touk.nussknacker.ui.config.{DesignerConfig, DesignerConfigLoader}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.metrics.RepositoryGauges
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.server.{AkkaHttpBasedRouteFactory, NussknackerHttpServer}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

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

      actorSystem                   <- createActorSystem(alreadyLoadedConfig)
      executionContextWithIORuntime <- ExecutionContextWithIORuntimeAdapter.createFrom(actorSystem.dispatcher)
      ioSttpBackend                 <- AsyncHttpClientCatsBackend.resource[IO]()

      dbRef           <- DbRef.create(alreadyLoadedConfig.rawConfig)
      metricsRegistry <- createGeneralPurposeMetricsRegistry()
      dbioRunner = DBIOActionRunner(dbRef)(executionContextWithIORuntime)

      infrastructureServices = InfrastructureServices(
        clock = clock,
        dbRef = dbRef,
        dbioRunner = dbioRunner,
        metricsRegistry = metricsRegistry,
        ioSttpBackend = ioSttpBackend
      )(
        executionContextWithIORuntime = executionContextWithIORuntime,
        actorSystem = actorSystem
      )
      domainServices <- DomainServices.create(designerConfigLoader, alreadyLoadedConfig, infrastructureServices)
      _ = initMetrics(metricsRegistry, alreadyLoadedConfig, domainServices.futureProcessRepository)

      route <- AkkaHttpBasedRouteFactory.createRoute(
        designerConfig = alreadyLoadedConfig,
        infrastructureServices = infrastructureServices,
        domainServices = domainServices
      )
      _ <- new NussknackerHttpServer(actorSystem).start(route, alreadyLoadedConfig, metricsRegistry)
      _ <- startJmxReporter(metricsRegistry)
      _ <- createStartAndStopLoggingEntries()
    } yield ()
  }

  private def createActorSystem(designerConfig: DesignerConfig) = {
    Resource
      .make(
        acquire = IO(ActorSystem("nussknacker-designer", designerConfig.rawConfig))
      )(
        release = system => {
          IO.fromFuture(IO(system.terminate())).map(_ => ())
        }
      )
  }

  private def createGeneralPurposeMetricsRegistry() = {
    Resource.pure[IO, MetricRegistry](new MetricRegistry)
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
      designerConfig.rawConfig.getDuration("repositoryGaugesCacheDuration"),
      processRepository
    )
      .prepareGauges()
  }

}

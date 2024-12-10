package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, SLF4JBridgeHandlerRegistrar}
import pl.touk.nussknacker.ui.config.processingtype.ProcessingTypeConfigsLoaderFactoryServiceLoader
import pl.touk.nussknacker.ui.config.root.DesignerRootConfigLoader
import pl.touk.nussknacker.ui.configloader.{ProcessingTypeConfigsLoader, ProcessingTypeConfigsLoaderFactory}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbFEStatisticsRepository
import pl.touk.nussknacker.ui.process.processingtype.loader.{
  ProcessingTypeDataLoader,
  ProcessingTypesConfigBasedProcessingTypeDataLoader
}
import pl.touk.nussknacker.ui.server.{AkkaHttpBasedRouteProvider, NussknackerHttpServer}
import pl.touk.nussknacker.ui.util.ActorSystemBasedExecutionContextWithIORuntime
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.time.Clock
import scala.concurrent.ExecutionContext

class NussknackerAppFactory(
    designerRootConfigLoader: DesignerRootConfigLoader,
    processingTypeConfigsLoaderFactory: ProcessingTypeConfigsLoaderFactory,
    createProcessingTypeDataLoader: ProcessingTypeConfigsLoader => ProcessingTypeDataLoader
) extends LazyLogging {

  def createApp(clock: Clock = Clock.systemUTC()): Resource[IO, Unit] = {
    for {
      rootConfig <- Resource.eval(designerRootConfigLoader.loadDesignerRootConfig())
      system     <- createActorSystem(rootConfig.rawConfig)
      executionContextWithIORuntime = ActorSystemBasedExecutionContextWithIORuntime.createFrom(system)
      sttpBackend <- createSttpBackend
      processingTypeConfigsLoader = processingTypeConfigsLoaderFactory.create(
        rootConfig,
        sttpBackend,
        executionContextWithIORuntime
      )
      processingTypeDataLoader = createProcessingTypeDataLoader(processingTypeConfigsLoader)
      materializer             = Materializer(system)
      _                      <- Resource.eval(IO(JavaClassVersionChecker.check()))
      _                      <- Resource.eval(IO(SLF4JBridgeHandlerRegistrar.register()))
      metricsRegistry        <- createGeneralPurposeMetricsRegistry()
      db                     <- DbRef.create(rootConfig.rawConfig.resolved)
      feStatisticsRepository <- QuestDbFEStatisticsRepository.create(system, clock, rootConfig.rawConfig.resolved)
      server = new NussknackerHttpServer(
        new AkkaHttpBasedRouteProvider(
          db,
          metricsRegistry,
          sttpBackend,
          processingTypeDataLoader,
          feStatisticsRepository,
          clock
        )(
          system,
          materializer,
          executionContextWithIORuntime
        ),
        system
      )
      _ <- server.start(rootConfig, metricsRegistry)
      _ <- startJmxReporter(metricsRegistry)
      _ <- createStartAndStopLoggingEntries()
    } yield ()
  }

  private def createSttpBackend = {
    Resource
      .make(
        acquire = IO(AsyncHttpClientFutureBackend.usingConfigBuilder(identity))
      )(
        release = backend => IO.fromFuture(IO(backend.close()))
      )
  }

  private def createActorSystem(config: ConfigWithUnresolvedVersion) = {
    Resource
      .make(
        acquire = IO(ActorSystem("nussknacker-designer", config.resolved))
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

}

object NussknackerAppFactory {

  def apply(designerRootConfigLoader: DesignerRootConfigLoader): NussknackerAppFactory = {
    val processingTypeConfigsLoaderFactory =
      ProcessingTypeConfigsLoaderFactoryServiceLoader.loadService(designerRootConfigLoader)
    new NussknackerAppFactory(
      designerRootConfigLoader,
      processingTypeConfigsLoaderFactory,
      new ProcessingTypesConfigBasedProcessingTypeDataLoader(_)
    )
  }

}

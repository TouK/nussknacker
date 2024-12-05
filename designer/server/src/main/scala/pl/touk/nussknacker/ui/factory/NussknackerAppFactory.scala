package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, SLF4JBridgeHandlerRegistrar}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbFEStatisticsRepository
import pl.touk.nussknacker.ui.loadableconfig.{
  EachTimeLoadingRootConfigLoadableProcessingTypeConfigs,
  LoadableDesignerRootConfig,
  LoadableProcessingTypeConfigs
}
import pl.touk.nussknacker.ui.process.processingtype.loader._
import pl.touk.nussknacker.ui.server.{AkkaHttpBasedRouteProvider, NussknackerHttpServer}

import java.time.Clock

class NussknackerAppFactory(
    loadableDesignerRootConfig: LoadableDesignerRootConfig,
    processingTypeDataLoader: ProcessingTypeDataLoader
) extends LazyLogging {

  def createApp(clock: Clock = Clock.systemUTC()): Resource[IO, Unit] = {
    for {
      rootConfig <- Resource.eval(loadableDesignerRootConfig.loadDesignerRootConfig())
      system     <- createActorSystem(rootConfig.rawConfig)
      materializer = Materializer(system)
      _                      <- Resource.eval(IO(JavaClassVersionChecker.check()))
      _                      <- Resource.eval(IO(SLF4JBridgeHandlerRegistrar.register()))
      metricsRegistry        <- createGeneralPurposeMetricsRegistry()
      db                     <- DbRef.create(rootConfig.rawConfig.resolved)
      feStatisticsRepository <- QuestDbFEStatisticsRepository.create(system, clock, rootConfig.rawConfig.resolved)
      server = new NussknackerHttpServer(
        new AkkaHttpBasedRouteProvider(
          db,
          metricsRegistry,
          processingTypeDataLoader,
          feStatisticsRepository,
          clock
        )(
          system,
          materializer
        ),
        system
      )
      _ <- server.start(rootConfig, metricsRegistry)
      _ <- startJmxReporter(metricsRegistry)
      _ <- createStartAndStopLoggingEntries()
    } yield ()
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

  def apply(loadableDesignerRootConfig: LoadableDesignerRootConfig): NussknackerAppFactory = {
    val loadableProcessingTypeConfig = LoadableProcessingTypeConfigs.default(loadableDesignerRootConfig)
    val processingTypeDataLoader = new ProcessingTypesConfigBasedProcessingTypeDataLoader(loadableProcessingTypeConfig)
    new NussknackerAppFactory(loadableDesignerRootConfig, processingTypeDataLoader)
  }

}

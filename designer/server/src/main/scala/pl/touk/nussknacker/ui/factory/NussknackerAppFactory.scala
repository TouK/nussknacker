package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.{IO, Resource}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, SLF4JBridgeHandlerRegistrar}
import pl.touk.nussknacker.ui.config.{DesignerConfig, DesignerConfigLoader}
import pl.touk.nussknacker.ui.configloader.{ProcessingTypeConfigsLoader, ProcessingTypeConfigsLoaderFactory}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbFEStatisticsRepository
import pl.touk.nussknacker.ui.process.processingtype.loader.{
  ProcessingTypeDataLoader,
  ProcessingTypesConfigBasedProcessingTypeDataLoader
}
import pl.touk.nussknacker.ui.server.{AkkaHttpBasedRouteProvider, NussknackerHttpServer}
import pl.touk.nussknacker.ui.util.{ActorSystemBasedExecutionContextWithIORuntime, IOToFutureSttpBackendConverter}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

import java.time.Clock

class NussknackerAppFactory(
    designerConfigLoader: DesignerConfigLoader,
    processingTypeConfigsLoaderFactoryOpt: Option[ProcessingTypeConfigsLoaderFactory],
    createProcessingTypeDataLoader: ProcessingTypeConfigsLoader => ProcessingTypeDataLoader
) extends LazyLogging {

  import net.ceedubs.ficus.Ficus._

  def createApp(clock: Clock = Clock.systemUTC()): Resource[IO, Unit] = {
    for {
      designerConfig <- Resource.eval(designerConfigLoader.loadDesignerConfig())
      system         <- createActorSystem(designerConfig.rawConfig)
      executionContextWithIORuntime = ActorSystemBasedExecutionContextWithIORuntime.createFrom(system)
      ioSttpBackend <- AsyncHttpClientCatsBackend.resource[IO]()
      processingTypeConfigsLoader = createProcessingTypeConfigsLoader(
        designerConfig,
        executionContextWithIORuntime,
        ioSttpBackend
      )
      processingTypeDataLoader = createProcessingTypeDataLoader(processingTypeConfigsLoader)
      materializer             = Materializer(system)
      _                      <- Resource.eval(IO(JavaClassVersionChecker.check()))
      _                      <- Resource.eval(IO(SLF4JBridgeHandlerRegistrar.register()))
      metricsRegistry        <- createGeneralPurposeMetricsRegistry()
      db                     <- DbRef.create(designerConfig.rawConfig.resolved)
      feStatisticsRepository <- QuestDbFEStatisticsRepository.create(system, clock, designerConfig.rawConfig.resolved)
      server = new NussknackerHttpServer(
        new AkkaHttpBasedRouteProvider(
          db,
          metricsRegistry,
          IOToFutureSttpBackendConverter.convert(ioSttpBackend)(executionContextWithIORuntime),
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
      _ <- server.start(designerConfig, metricsRegistry)
      _ <- startJmxReporter(metricsRegistry)
      _ <- createStartAndStopLoggingEntries()
    } yield ()
  }

  private def createProcessingTypeConfigsLoader(
      designerConfig: DesignerConfig,
      executionContextWithIORuntime: ActorSystemBasedExecutionContextWithIORuntime,
      sttpBackend: SttpBackend[IO, Any]
  ): ProcessingTypeConfigsLoader = {
    processingTypeConfigsLoaderFactoryOpt
      .map { factory =>
        logger.debug(
          s"Found custom ${classOf[ProcessingTypeConfigsLoaderFactory].getSimpleName}: ${factory.getClass.getName}. Using it for configuration loading"
        )
        factory.create(
          designerConfig.rawConfig.resolved.getAs[Config]("configLoader").getOrElse(ConfigFactory.empty()),
          sttpBackend,
        )(executionContextWithIORuntime)
      }
      .getOrElse {
        logger.debug(
          s"No custom ${classOf[ProcessingTypeConfigsLoaderFactory].getSimpleName} found. Using the default implementation of loader"
        )
        () => designerConfigLoader.loadDesignerConfig().map(_.processingTypeConfigs)
      }
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

  def apply(designerConfigLoader: DesignerConfigLoader): NussknackerAppFactory = {
    val processingTypeConfigsLoaderFactoryOpt =
      ScalaServiceLoader.loadOne[ProcessingTypeConfigsLoaderFactory](getClass.getClassLoader)
    new NussknackerAppFactory(
      designerConfigLoader,
      processingTypeConfigsLoaderFactoryOpt,
      new ProcessingTypesConfigBasedProcessingTypeDataLoader(_)
    )
  }

}

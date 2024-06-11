package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, SLF4JBridgeHandlerRegistrar}
import pl.touk.nussknacker.ui.config.DesignerConfigLoader
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.timeseries.questdb.{QuestDbConfig, QuestDbFEStatisticsRepository}
import pl.touk.nussknacker.ui.db.timeseries.{FEStatisticsRepository, NopFEStatisticsRepository}
import pl.touk.nussknacker.ui.server.{AkkaHttpBasedRouteProvider, NussknackerHttpServer}

import java.time.Clock
import scala.concurrent.Future

class NussknackerAppFactory(processingTypeDataStateFactory: ProcessingTypeDataStateFactory) extends LazyLogging {

  def this() = {
    this(
      ProcessingTypeDataReaderBasedProcessingTypeDataStateFactory
    )
  }

  def createApp(
      baseUnresolvedConfig: Config = ConfigFactoryExt.parseUnresolved(classLoader = getClass.getClassLoader),
      clock: Clock = Clock.systemUTC()
  ): Resource[IO, Unit] = {
    for {
      config <- designerConfigFrom(baseUnresolvedConfig)
      system <- createActorSystem(config)
      materializer = Materializer(system)
      _                      <- Resource.eval(IO(JavaClassVersionChecker.check()))
      _                      <- Resource.eval(IO(SLF4JBridgeHandlerRegistrar.register()))
      metricsRegistry        <- createGeneralPurposeMetricsRegistry()
      db                     <- DbRef.create(config.resolved)
      questDbConfig          <- Resource.eval(IO(QuestDbConfig.apply(config.resolved)))
      feStatisticsRepository <- createFEStatisticsRepository(clock, questDbConfig, system)
      server = new NussknackerHttpServer(
        new AkkaHttpBasedRouteProvider(db, metricsRegistry, processingTypeDataStateFactory, feStatisticsRepository)(
          system,
          materializer
        ),
        system
      )
      _ <- server.start(config, metricsRegistry)
      _ <- startJmxReporter(metricsRegistry)
      _ <- createStartAndStopLoggingEntries()
    } yield ()
  }

  private def designerConfigFrom(baseUnresolvedConfig: Config) = {
    Resource.eval(IO(DesignerConfigLoader.load(baseUnresolvedConfig, getClass.getClassLoader)))
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

  private def createFEStatisticsRepository(
      clock: Clock,
      config: QuestDbConfig,
      system: ActorSystem
  ): Resource[IO, FEStatisticsRepository[Future]] = {
    val repository = if (config.enabled) {
      QuestDbFEStatisticsRepository.create(system, clock, config)
    } else {
      logger.info("QuestDb is disabled - collecting FE statistics is skipped")
      createNopFEStatisticRepository
    }
    repository.handleErrorWith { t: Throwable =>
      logger.warn("Creating QuestDb failed", t)
      createNopFEStatisticRepository
    }
  }

  private def createNopFEStatisticRepository: Resource[IO, FEStatisticsRepository[Future]] = {
    Resource.eval(IO(new NopFEStatisticsRepository()))
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

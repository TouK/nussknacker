package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.{ContextShift, IO, Resource}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import io.dropwizard.metrics5.jmx.JmxReporter
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, SLF4JBridgeHandlerRegistrar}
import pl.touk.nussknacker.ui.config.DesignerConfigLoader
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DbConfig}
import pl.touk.nussknacker.ui.server.{AkkaHttpBasedRouteProvider, NussknackerHttpServer}
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}

class NussknackerApp(baseUnresolvedConfig: Config,
                     processingTypeDataProviderFactory: ProcessingTypeDataProviderFactory)
  extends LazyLogging {

  def this() = {
    this(
      ConfigFactoryExt.parseUnresolved(classLoader = getClass.getClassLoader),
      ProcessingTypeDataReaderBasedProcessingTypeDataProviderFactory
    )
  }

  def this(baseUnresolvedConfig: Config) = {
    this(baseUnresolvedConfig, ProcessingTypeDataReaderBasedProcessingTypeDataProviderFactory)
  }

  def this(processingTypeDataProviderFactory: ProcessingTypeDataProviderFactory) = {
    this(ConfigFactoryExt.parseUnresolved(classLoader = getClass.getClassLoader), processingTypeDataProviderFactory)
  }

  def init(): Resource[IO, Unit] = {
    for {
      config <- designerConfigFrom(baseUnresolvedConfig)
      system <- createActorSystem(config)
      materializer = Materializer(system)
      _ <- Resource.eval(IO(JavaClassVersionChecker.check()))
      _ <- Resource.eval(IO(SLF4JBridgeHandlerRegistrar.register()))
      metricsRegistry <- createGeneralPurposeMetricsRegistry()
      db <- initDb(config)
      server = new NussknackerHttpServer(
        new AkkaHttpBasedRouteProvider(db, metricsRegistry, processingTypeDataProviderFactory)(system, materializer),
        system,
        materializer
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
          implicit val contextShift: ContextShift[IO] = IO.contextShift(system.dispatcher)
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

  private def initDb(config: ConfigWithUnresolvedVersion): Resource[IO, DbConfig] = {
    val resolvedConfig = config.resolved
    for {
      db <- Resource
        .make(
          acquire = IO(JdbcBackend.Database.forConfig("db", resolvedConfig))
        )(
          release = db => IO(db.close())
        )
      _ <- Resource.eval(IO(DatabaseInitializer.initDatabase("db", resolvedConfig)))
    } yield DbConfig(db, chooseDbProfile(resolvedConfig))
  }

  private def chooseDbProfile(config: Config): JdbcProfile = {
    val jdbcUrlPattern = "jdbc:([0-9a-zA-Z]+):.*".r
    config.getAs[String]("db.url") match {
      case Some(jdbcUrlPattern("postgresql")) => PostgresProfile
      case Some(jdbcUrlPattern("hsqldb")) => HsqldbProfile
      case None => HsqldbProfile
      case _ => throw new IllegalStateException("unsupported jdbc url")
    }
  }
}

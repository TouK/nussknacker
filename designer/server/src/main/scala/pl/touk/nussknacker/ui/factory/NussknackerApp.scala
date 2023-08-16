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
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}

class NussknackerApp(baseUnresolvedConfig: Config)
  extends LazyLogging {

  def this() = {
    this(ConfigFactoryExt.parseUnresolved(classLoader = getClass.getClassLoader))
  }

  protected val config: ConfigWithUnresolvedVersion = DesignerConfigLoader.load(baseUnresolvedConfig, getClass.getClassLoader)

  def init(): Resource[IO, Unit] = {
    for {
      system <- createActorSystem()
      materializer = Materializer(system)
      _ <- Resource.eval(IO(JavaClassVersionChecker.check()))
      _ <- Resource.eval(IO(SLF4JBridgeHandlerRegistrar.register()))
      metricsRegistry <- createGeneralPurposeMetricsRegistry()
      db = initDb(config.resolved)
      server = new NussknackerHttpServer(system, materializer, system.dispatcher)
      _ <- server.start(config, db, metricsRegistry)
      _ <- startJmxReporter(metricsRegistry)
      _ <- createStartAndStopLoggingEntries()
    } yield ()
  }

  private def createActorSystem() = {
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

  private def initDb(config: Config): DbConfig = {
    val db = JdbcBackend.Database.forConfig("db", config)
    val profile = chooseDbProfile(config)
    val dbConfig = DbConfig(db, profile)
    DatabaseInitializer.initDatabase("db", config)

    dbConfig
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

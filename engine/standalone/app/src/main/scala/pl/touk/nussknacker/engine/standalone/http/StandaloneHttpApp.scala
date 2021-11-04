package pl.touk.nussknacker.engine.standalone.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.EngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.standalone.deployment.DeploymentService
import pl.touk.nussknacker.engine.standalone.http.logging.StandaloneRequestResponseLogger
import pl.touk.nussknacker.engine.standalone.http.metrics.dropwizard.influxdb.StandaloneInfluxDbReporter
import pl.touk.nussknacker.engine.standalone.http.metrics.dropwizard.StandaloneMetricsReporter
import pl.touk.nussknacker.engine.baseengine.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.util.Try
import scala.util.control.NonFatal

object StandaloneHttpApp extends Directives with FailFastCirceSupport with LazyLogging with App {

  private val config = ConfigFactory.load()

  implicit val system: ActorSystem = ActorSystem("nussknacker-standalone-http", config)

  import system.dispatcher

  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  val metricRegistry = StandaloneMetrics.prepareRegistry(config)

  val standaloneApp = new StandaloneHttpApp(config, metricRegistry)

  val managementPort = Try(args(0).toInt).getOrElse(8070)
  val processesPort = Try(args(1).toInt).getOrElse(8080)

  Http().bindAndHandle(
    standaloneApp.managementRoute.route,
    interface = "0.0.0.0",
    port = managementPort
  )

  Http().bindAndHandle(
    standaloneApp.processRoute.route(StandaloneRequestResponseLogger.get(Thread.currentThread.getContextClassLoader)),
    interface = "0.0.0.0",
    port = processesPort
  )

}

object StandaloneMetrics extends LazyLogging {

  def prepareRegistry(config: Config): MetricRegistry = {
    val metricRegistry = new MetricRegistry
    val metricReporters = loadMetricsReporters()
    if (metricReporters.nonEmpty) {
      metricReporters.foreach { reporter =>
        reporter.createAndRunReporter(metricRegistry, config)
      }
    } else {
      StandaloneInfluxDbReporter.createAndRunReporterIfConfigured(metricRegistry, config)
    }
    metricRegistry
  }

  private def loadMetricsReporters(): List[StandaloneMetricsReporter] = {
    try {
      val reporters = ScalaServiceLoader.load[StandaloneMetricsReporter](Thread.currentThread().getContextClassLoader)
      logger.info(s"Loaded metrics reporters: ${reporters.map(_.getClass.getCanonicalName).mkString(", ")}")
      reporters
    } catch {
      case NonFatal(ex) =>
        logger.warn("Metrics reporter load failed. There will be no metrics reporter in standalone", ex)
        List.empty
    }
  }
}


class StandaloneHttpApp(config: Config, metricRegistry: MetricRegistry)(implicit as: ActorSystem)
  extends Directives with LazyLogging {

  private val contextPreparer = new EngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

  private val deploymentService = DeploymentService(contextPreparer, config)

  val managementRoute = new ManagementRoute(deploymentService)

  val processRoute = new ProcessRoute(deploymentService)

}

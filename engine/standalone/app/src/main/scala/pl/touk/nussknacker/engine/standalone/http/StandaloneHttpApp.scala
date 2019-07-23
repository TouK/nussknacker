package pl.touk.nussknacker.engine.standalone.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.http.argonaut.{Argonaut62Support, JsonMarshaller}
import pl.touk.nussknacker.engine.standalone.deployment.DeploymentService
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.nussknacker.engine.standalone.utils.logging.StandaloneRequestResponseLogger
import pl.touk.nussknacker.engine.standalone.utils.metrics.StandaloneMetricsReporter
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.util.Try
import scala.util.control.NonFatal

object StandaloneHttpApp extends Directives with Argonaut62Support with LazyLogging with App {

  private val config = ConfigFactory.load()

  implicit val system: ActorSystem = ActorSystem("nussknacker-standalone-http", config)

  import system.dispatcher

  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  implicit private val jsonMarshaller: JsonMarshaller = JsonMarshaller.prepareDefault(config)

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
    metricReporters.foreach { reporter =>
      reporter.createAndRunReporter(metricRegistry, config)
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
  extends Directives with Argonaut62Support with LazyLogging {

  private val contextPreparer = new StandaloneContextPreparer(metricRegistry)

  private val deploymentService = DeploymentService(contextPreparer, config)

  val managementRoute = new ManagementRoute(deploymentService)

  val processRoute = new ProcessRoute(deploymentService)

}

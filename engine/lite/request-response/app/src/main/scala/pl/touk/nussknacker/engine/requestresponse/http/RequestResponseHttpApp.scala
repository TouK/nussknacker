package pl.touk.nussknacker.engine.requestresponse.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.{LiteEngineMetrics, DropwizardMetricsProviderFactory}
import pl.touk.nussknacker.engine.requestresponse.deployment.DeploymentService
import pl.touk.nussknacker.engine.requestresponse.http.logging.RequestResponseLogger

import scala.util.Try

object RequestResponseHttpApp extends Directives with FailFastCirceSupport with LazyLogging with App {

  private val config = ConfigFactory.load()

  implicit val system: ActorSystem = ActorSystem("nussknacker-request-response-http", config)

  import system.dispatcher

  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  val metricRegistry = LiteEngineMetrics.prepareRegistry(config)

  val requestResponseApp = new RequestResponseHttpApp(config, metricRegistry)

  val managementPort = Try(args(0).toInt).getOrElse(8070)
  val processesPort = Try(args(1).toInt).getOrElse(8080)

  Http().bindAndHandle(
    requestResponseApp.managementRoute.route,
    interface = "0.0.0.0",
    port = managementPort
  )

  Http().bindAndHandle(
    requestResponseApp.processRoute.route(RequestResponseLogger.get(Thread.currentThread.getContextClassLoader)),
    interface = "0.0.0.0",
    port = processesPort
  )

}


class RequestResponseHttpApp(config: Config, metricRegistry: MetricRegistry)(implicit as: ActorSystem)
  extends Directives with LazyLogging {

  private val contextPreparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

  private val deploymentService = DeploymentService(contextPreparer, config)

  val managementRoute = new ManagementRoute(deploymentService)

  val processRoute = new ProcessRoute(deploymentService)

}

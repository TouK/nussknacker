package pl.touk.nussknacker.engine.standalone.http

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter, GraphiteUDP}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.standalone.deployment.DeploymentService
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer

import scala.util.Try

object StandaloneHttpApp extends Directives with Argonaut62Support with LazyLogging with App {

  private val config = ConfigFactory.load()

  implicit val system = ActorSystem("nussknacker-standalone-http", config)

  import system.dispatcher

  implicit private val materializer = ActorMaterializer()

  private val metricRegistry = new MetricRegistry

  GraphiteReporter.forRegistry(metricRegistry)
    .prefixedWith(s"${config.getString("standaloneProcessConfig.environment")}.${config.getString("hostName")}.standaloneEngine")
    .build(graphiteSender).start(10, TimeUnit.SECONDS)


  val standaloneApp = new StandaloneHttpApp(config, metricRegistry)

  val managementPort = Try(args(0).toInt).getOrElse(8070)
  val processesPort = Try(args(1).toInt).getOrElse(8080)

  Http().bindAndHandle(
    standaloneApp.managementRoute.route,
    interface = "0.0.0.0",
    port = managementPort
  )

  Http().bindAndHandle(
    standaloneApp.processRoute.route,
    interface = "0.0.0.0",
    port = processesPort
  )

  private def graphiteSender = {
    if (config.hasPath("graphite.protocol") && "udp".equals(config.getString("graphite.protocol"))) {
      new GraphiteUDP(config.getString("graphite.hostName"), config.getInt("graphite.port"))
    } else {
      new Graphite(config.getString("graphite.hostName"), config.getInt("graphite.port"))
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

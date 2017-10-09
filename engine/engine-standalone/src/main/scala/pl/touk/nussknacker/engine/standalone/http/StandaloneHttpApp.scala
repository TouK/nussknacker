package pl.touk.nussknacker.engine.standalone.http

import java.io.File
import java.net.URLClassLoader

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.standalone.management.DeploymentService
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.{JarClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.{ClassLoaderModelData, ModelData}
import pl.touk.nussknacker.engine.api.conversion.ProcessConfigCreatorMapping

import scala.util.Try

object StandaloneHttpApp extends Directives with Argonaut62Support with LazyLogging {

  implicit val system = ActorSystem("nussknacker-standalone-http")

  import system.dispatcher

  implicit private val materializer = ActorMaterializer()

  private val config = ConfigFactory.load()

  private val deploymentService = DeploymentService(prepareContext(config), config)

  def main(args: Array[String]): Unit = {
    val ports = for {
      mgmPort <- Try(args(0).toInt).toOption
      processesPort <- Try(args(1).toInt).toOption
    } yield (mgmPort, processesPort)
    ports match {
      case Some((mgmPort, procPort)) => initHttp(mgmPort, procPort)
      case None => initHttp()
    }
  }

  val managementRoute = new ManagementRoute(deploymentService)

  val processRoute = new ProcessRoute(deploymentService)


  def initHttp(managementPort: Int = 8070, processesPort: Int = 8080) = {
    Http().bindAndHandle(
      managementRoute.route,
      interface = "0.0.0.0",
      port = managementPort
    )

    Http().bindAndHandle(
      processRoute.route,
      interface = "0.0.0.0",
      port = processesPort
    )

  }

  def loadProcessesClassloader(config: Config): ClassLoader = {
    if (!config.hasPath("jarPath")) { //this is ugly but we want to be able to test without jar for now
      getClass.getClassLoader
    } else {
      JarClassLoader(config.getString("jarPath")).classLoader
    }
  }

  private def prepareContext(config: Config): StandaloneContextPreparer = {
    val metricRegistry = new MetricRegistry
    GraphiteReporter.forRegistry(metricRegistry)
      .prefixedWith(s"standaloneEngine.${config.getString("hostName")}")
        .build(new Graphite(config.getString("graphite.hostName"), config.getInt("graphite.port")))
    new StandaloneContextPreparer(metricRegistry)
  }

}
package pl.touk.esp.engine.standalone.http

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
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.standalone.management.DeploymentService
import pl.touk.esp.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.esp.engine.util.ThreadUtils
import pl.touk.esp.engine.util.loader.JarClassLoader
import pl.touk.http.argonaut.Argonaut62Support

import scala.util.Try

object StandaloneHttpApp extends Directives with Argonaut62Support with LazyLogging {

  implicit val system = ActorSystem("esp-standalone-http")

  import system.dispatcher

  implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()
  val processesClassLoader = loadProcessesClassloader(config)
  val creator = loadCreator(config)


  val deploymentService = DeploymentService(prepareContext(config), creator, config)

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


  val managementRoute = new ManagementRoute(processesClassLoader, deploymentService)

  val processRoute = new ProcessRoute(processesClassLoader, deploymentService)


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

  def loadCreator(config: Config): ProcessConfigCreator = {
    ThreadUtils.withThisAsContextClassLoader(processesClassLoader) {
      ThreadUtils.loadUsingContextLoader(config.getString("processConfigCreatorClass")).newInstance().asInstanceOf[ProcessConfigCreator]
    }
  }

  def loadProcessesClassloader(config: Config): ClassLoader = {
    if (!config.hasPath("jarPath")) { //to troche slabe, ale na razie chcemy jakos testowac bez jara...
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
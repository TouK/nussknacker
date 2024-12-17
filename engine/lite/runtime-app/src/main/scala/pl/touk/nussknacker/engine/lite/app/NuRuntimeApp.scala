package pl.touk.nussknacker.engine.lite.app

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, ResourceLoader, SLF4JBridgeHandlerRegistrar, UriUtils}

import java.nio.file.Path
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

object NuRuntimeApp extends App with LazyLogging {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  JavaClassVersionChecker.check()
  SLF4JBridgeHandlerRegistrar.register()

  val (scenarioFileLocation, deploymentConfigLocation) = parseArgs
  val scenario                                         = parseScenario(scenarioFileLocation)
  val deploymentConfig                                 = parseDeploymentConfig(deploymentConfigLocation)

  val runtimeConfig = {
    val configLocationsProperty: String = "nussknacker.config.locations"
    val locationsPropertyValueOpt       = Option(System.getProperty(configLocationsProperty))
    val locations = locationsPropertyValueOpt.map(UriUtils.extractListOfLocations).getOrElse(List.empty)
    ConfigFactory.load(new ConfigFactoryExt(getClass.getClassLoader).parseUnresolved(locations))
  }

  val httpConfig = runtimeConfig.as[HttpBindingConfig]("http")

  implicit val system: ActorSystem = ActorSystem("nu-lite-runtime", runtimeConfig)

  import system.dispatcher

  private val akkaHttpCloseTimeout = 10 seconds

  // Because actor system creates non-daemon threads, all exceptions from current thread will be suppressed and process
  // will be still alive even if something fail (like scenarioInterpreter creation)
  val exitCode =
    try {
      runAfterActorSystemCreation()
      0
    } catch {
      case NonFatal(ex) =>
        logger.error("Exception during runtime execution", ex)
        1
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }

  System.exit(exitCode)

  private def runAfterActorSystemCreation(): Unit = {
    val scenarioInterpreter =
      RunnableScenarioInterpreterFactory.prepareScenarioInterpreter(scenario, runtimeConfig, deploymentConfig, system)

    val healthCheckProvider = new HealthCheckRoutesProvider(system, scenarioInterpreter)

    val httpServer = Http().newServerAt(interface = httpConfig.interface, port = httpConfig.port)

    val runFuture         = scenarioInterpreter.run()
    val healthCheckRoutes = healthCheckProvider.routes
    val routes            = Directives.concat(scenarioInterpreter.routes.toList ::: healthCheckRoutes :: Nil: _*)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        logger.info("Closing RunnableScenarioInterpreter")
        scenarioInterpreter.close()
      }
    })

    @volatile var server: ServerBinding = null
    val boundRoutesFuture = httpServer.bind(routes).map { b =>
      logger.info(s"Http server started on ${httpConfig.interface}:${httpConfig.port}")
      server = b
    }

    try {
      Await.result(Future.sequence(List(runFuture, boundRoutesFuture)), Duration.Inf)
    } finally {
      logger.info("Closing application NuRuntimeApp")
      scenarioInterpreter.close() // in case of exception during binding
      if (server != null) Await.ready(server.terminate(akkaHttpCloseTimeout), akkaHttpCloseTimeout)
    }
  }

  private def parseArgs: (Path, Path) = {
    if (args.length < 1) {
      missingArgumentError("scenario_file_location")
    } else if (args.length < 2) {
      missingArgumentError("deployment_config_location")
    }
    (Path.of(args(0)), Path.of(args(1)))
  }

  private def missingArgumentError(argumentName: String): Unit = {
    System.err.println(s"Missing $argumentName argument!")
    System.err.println("")
    System.err.println("Usage: ./run.sh scenario_file_location.json deployment_config_location.conf")
    sys.exit(1)
  }

  private def parseScenario(location: Path): CanonicalProcess = {
    val scenarioString = ResourceLoader.load(location)
    logger.info(s"Running scenario: $scenarioString")

    val parsedScenario = ScenarioParser.parse(scenarioString)
    parsedScenario.valueOr { err =>
      System.err.println("Scenario file is not a valid json")
      System.err.println(s"Errors found: ${err.toList.mkString(", ")}")
      sys.exit(2)
    }
  }

  private def parseDeploymentConfig(path: Path): Config = {
    ConfigFactory.parseFile(path.toFile)
  }

}

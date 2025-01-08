package pl.touk.nussknacker.engine.lite.app

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import cats.data.{NonEmptyList, Validated}
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.RunnableScenarioInterpreter
import pl.touk.nussknacker.engine.lite.app.NuRuntimeApp.AppStartingError.{CannotParseScenario, MissingArgument}
import pl.touk.nussknacker.engine.lite.app.RunnableScenarioInterpreterFactory.prepareScenarioInterpreter
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.engine.util.{JavaClassVersionChecker, ResourceLoader, SLF4JBridgeHandlerRegistrar, UriUtils}

import java.nio.file.Path
import scala.concurrent.duration._
import scala.util.control.NonFatal

object NuRuntimeApp extends IOApp with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    createProgram(args).useForever
      .handleError {
        case AppStartingError.MissingArgument(argumentName) =>
          logger.error(s"Missing $argumentName argument!")
          logger.error("")
          logger.error("Usage: ./run.sh scenario_file_location.json deployment_config_location.conf")
          ExitCode(1)
        case AppStartingError.CannotParseScenario(errors) =>
          logger.error("Scenario file is not a valid json")
          logger.error(s"Errors found: ${errors.toList.mkString(", ")}")
          ExitCode(2)
        case NonFatal(ex) =>
          logger.error("Application failed", ex)
          ExitCode.Error
      }
      .as(ExitCode.Success)
  }

  private def createProgram(args: List[String]) = for {
    parsedArgs <- parseArgs(args)
    (scenarioFileLocation, deploymentConfigLocation) = parsedArgs
    runtimeConfig       <- loadRuntimeConfig()
    deploymentConfig    <- loadDeploymentConfig(deploymentConfigLocation)
    _                   <- doPrerequisites()
    system              <- createActorSystem(runtimeConfig)
    scenario            <- parseScenario(scenarioFileLocation)
    scenarioInterpreter <- createScenarioInterpreter(system, runtimeConfig, deploymentConfig, scenario)
    routes = createRoutes(system, scenarioInterpreter)
    _ <- createAndRunServer(runtimeConfig, routes)(system)
  } yield ()

  private def doPrerequisites() = Resource
    .make(
      acquire = for {
        _ <- IO.delay(logger.info("Running NuRuntimeApp"))
        _ <- IO.delay {
          JavaClassVersionChecker.check()
          SLF4JBridgeHandlerRegistrar.register()
        }
      } yield ()
    )(
      release = _ => IO.delay(logger.info("Closing NuRuntimeApp"))
    )

  private def loadRuntimeConfig() = {
    Resource.eval(IO.delay {
      val configLocationsProperty: String = "nussknacker.config.locations"
      val locationsPropertyValueOpt       = Option(System.getProperty(configLocationsProperty))
      val locations = locationsPropertyValueOpt.map(UriUtils.extractListOfLocations).getOrElse(List.empty)
      ConfigFactory.load(new ConfigFactoryExt(getClass.getClassLoader).parseUnresolved(locations))
    })
  }

  private def createActorSystem(config: Config) = {
    Resource.make(
      acquire = IO(ActorSystem("nu-lite-runtime", config))
    )(
      release = system => IO.fromFuture(IO(system.terminate())).map(_ => ()).timeout(5 seconds)
    )
  }

  private def createScenarioInterpreter(
      system: ActorSystem,
      runtimeConfig: Config,
      deploymentConfig: Config,
      scenario: CanonicalProcess
  ) = {
    for {
      _                   <- Resource.eval(IO.delay(logger.info("Preparing RunnableScenarioInterpreter")))
      scenarioInterpreter <- prepareScenarioInterpreter(scenario, runtimeConfig, deploymentConfig, system)
      _ <- Resource
        .make(
          acquire = for {
            _ <- IO.delay(logger.info("Running RunnableScenarioInterpreter"))
            _ <- IO.delay(scenarioInterpreter.run())
          } yield ()
        )(
          release = _ => IO.delay(logger.info("Closing RunnableScenarioInterpreter"))
        )
    } yield scenarioInterpreter
  }

  private def createRoutes(system: ActorSystem, scenarioInterpreter: RunnableScenarioInterpreter): Route = {
    val healthCheckProvider = new HealthCheckRoutesProvider(system, scenarioInterpreter)
    Directives.concat(scenarioInterpreter.routes.toList ::: healthCheckProvider.routes :: Nil: _*)
  }

  private def createAndRunServer(runtimeConfig: Config, routes: Route)(
      implicit system: ActorSystem
  ): Resource[IO, Unit] = {
    Resource
      .make(
        acquire = for {
          httpConfig <- IO.delay(runtimeConfig.as[HttpBindingConfig]("http"))
          _          <- IO.delay(logger.info(s"Starting HTTP server on ${httpConfig.interface}:${httpConfig.port}"))
          binding <- IO.fromFuture {
            IO(
              Http()
                .newServerAt(interface = httpConfig.interface, port = httpConfig.port)
                .bind(routes)
            )
          }
        } yield binding
      )(
        release = binding =>
          for {
            _ <- IO.delay(logger.info("Stopping HTTP server"))
            _ <- IO.fromFuture(IO(binding.terminate(10 seconds)))
          } yield ()
      )
      .map(_ => ())
  }

  private def parseArgs(args: List[String]): Resource[IO, (Path, Path)] = Resource.eval(IO.delay {
    if (args.length < 1) {
      throw MissingArgument("scenario_file_location")
    } else if (args.length < 2) {
      throw MissingArgument("deployment_config_location")
    }
    (Path.of(args(0)), Path.of(args(1)))
  })

  private def parseScenario(location: Path) = Resource.eval {
    IO.delay {
      val scenarioString = ResourceLoader.load(location)
      logger.info(s"Running scenario: $scenarioString")
      ScenarioParser.parse(scenarioString) match {
        case Validated.Valid(scenario) => scenario
        case Validated.Invalid(errors) => throw CannotParseScenario(errors)
      }
    }
  }

  private def loadDeploymentConfig(path: Path) = Resource.eval {
    IO.delay {
      ConfigFactory.parseFile(path.toFile)
    }
  }

  sealed trait AppStartingError extends Throwable

  object AppStartingError {
    final case class MissingArgument(name: String)                     extends AppStartingError
    final case class CannotParseScenario(errors: NonEmptyList[String]) extends AppStartingError
  }

}

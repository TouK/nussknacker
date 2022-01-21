package pl.touk.nussknacker.engine.lite.kafka

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.{DropwizardMetricsProviderFactory, LiteMetricRegistryFactory}
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.util.JavaClassVersionChecker
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import java.net.URL
import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.duration._

object NuKafkaRuntimeApp extends App with LazyLogging {

  JavaClassVersionChecker.check()

  val (scenarioFileLocation, deploymentDataLocation) = parseArgs

  val scenario = parseScenario(scenarioFileLocation)

  val liteKafkaJobData = parseDeploymentData(deploymentDataLocation)

  val runtimeConfig = ConfigFactory.load(ConfigFactoryExt.parseUnresolved(classLoader = getClass.getClassLoader))

  val system = ActorSystem("nu-kafka-runtime", runtimeConfig)
  import system.dispatcher

  val scenarioInterpreter = prepareScenarioInterpreter(runtimeConfig, liteKafkaJobData)
  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      logger.info("Closing KafkaTransactionalScenarioInterpreter")
      scenarioInterpreter.close()
    }
  })

  private val healthCheckServer = new HealthCheckServerRunner(system, scenarioInterpreter)
  Await.result(for {
    _ <- healthCheckServer.start()
    _ <- scenarioInterpreter.run()
  } yield (), Duration.Inf)

  logger.info(s"Closing application NuKafkaRuntimeApp")

  Await.ready(healthCheckServer.stop(), 5.seconds)
  Await.result(system.terminate(), 5.seconds)

  private def parseArgs: (Path, Path) = {
    if (args.length < 1) {
      missingArgumentError("scenario_file_location")
    } else if (args.length < 2) {
      missingArgumentError("deployment_data_location")
    }
    (Path.of(args(0)), Path.of(args(1)))
  }

  private def missingArgumentError(argumentName: String): Unit = {
    System.err.println(s"Missing $argumentName argument!")
    System.err.println("")
    System.err.println("Usage: ./run.sh scenario_file_location.json deployment_data_location.conf")
    sys.exit(1)
  }

  private def parseScenario(location: Path): EspProcess = {
    val scenarioString = FileUtils.readFileToString(location.toFile)
    logger.info(s"Running scenario: $scenarioString")
    val parsedScenario = ScenarioParser.parse(scenarioString)
    parsedScenario.valueOr { err =>
      System.err.println("Scenario file is not a valid json")
      System.err.println(s"Errors found: ${err.toList.mkString(", ")}")
      sys.exit(2)
    }
  }

  private def parseDeploymentData(path: Path): LiteKafkaJobData = {
    ConfigFactory.parseFile(path.toFile).as[LiteKafkaJobData]
  }

  private def prepareScenarioInterpreter(runtimeConfig: Config, liteKafkaJobData: LiteKafkaJobData): KafkaTransactionalScenarioInterpreter = {
    val modelConfig: Config = runtimeConfig.getConfig("modelConfig")

    val modelData = ModelData(modelConfig, ModelClassLoader(modelConfig.as[List[URL]]("classPath")))

    val metricRegistry = prepareMetricRegistry(runtimeConfig)
    val preparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))
    // TODO Pass correct ProcessVersion and DeploymentData
    val jobData = JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)

    KafkaTransactionalScenarioInterpreter(scenario, jobData, liteKafkaJobData, modelData, preparer)
  }

  private def prepareMetricRegistry(engineConfig: Config) = {
    lazy val instanceId = sys.env.getOrElse("INSTANCE_ID", LiteMetricRegistryFactory.hostname)
    new LiteMetricRegistryFactory(instanceId).prepareRegistry(engineConfig)
  }

}
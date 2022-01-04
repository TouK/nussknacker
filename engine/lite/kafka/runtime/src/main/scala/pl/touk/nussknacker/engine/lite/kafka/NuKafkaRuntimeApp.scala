package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.{DropwizardMetricsProviderFactory, LiteEngineMetrics}
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import java.net.URL
import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object NuKafkaRuntimeApp extends App with LazyLogging {

  val scenarioFileLocation = parseArgs

  val scenario = parseScenario

  val scenarioInterpreter: KafkaTransactionalScenarioInterpreter = prepareScenarioInterpreter

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      logger.info("Closing KafkaTransactionalScenarioInterpreter")
      scenarioInterpreter.close()
    }
  })

  Await.result(scenarioInterpreter.run(), Duration.Inf)
  logger.info(s"Closing application NuKafkaRuntimeApp")

  private def parseArgs: Path = {
    if (args.length < 1) {
      System.err.println("Missing scenario_file_location argument!")
      System.err.println("")
      System.err.println("Usage: ./run.sh scenario_file_location.json")
      sys.exit(1)
    }

    Path.of(args(0))
  }

  private def parseScenario: EspProcess = {
    val scenarioString = FileUtils.readFileToString(scenarioFileLocation.toFile)
    logger.info(s"Running scenario: $scenarioString")
    val parsedScenario = ScenarioParser.parse(scenarioString)
    parsedScenario.valueOr { err =>
      System.err.println("Scenario file is not a valid json")
      System.err.println(s"Errors found: ${err.toList.mkString(", ")}")
      sys.exit(2)
    }
  }

  private def prepareScenarioInterpreter: KafkaTransactionalScenarioInterpreter = {
    val engineConfig = ConfigFactory.load(ConfigFactoryExt.parseUnresolved(classLoader = getClass.getClassLoader))
    val modelConfig: Config = engineConfig.getConfig("modelConfig")

    val modelData = ModelData(modelConfig, ModelClassLoader(modelConfig.as[List[URL]]("classPath")))

    val metricRegistry = LiteEngineMetrics.prepareRegistry(engineConfig)
    val preparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))
    // TODO Pass correct ProcessVersion and DeploymentData
    val jobData = JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)

    KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData, preparer)
  }

}

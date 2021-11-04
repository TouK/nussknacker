package pl.touk.nussknacker.engine.baseengine.kafka

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.EngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import java.io.File
import java.lang.Thread.UncaughtExceptionHandler
import java.net.URL
import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Using

object NuKafkaEngineApp extends App with LazyLogging {

  val scenarioFileLocation = parseArgs

  val scenario = parseScenario

  val scenarioInterpreter = prepareScenarioInterpreter

  Using.resource(scenarioInterpreter) { interpreter =>
    interpreter.run()
  }

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
    val validatedCanonicalScenario = ProcessMarshaller.fromJson(FileUtils.readFileToString(scenarioFileLocation.toFile))

    val canonicalScenario = validatedCanonicalScenario.valueOr { err =>
      System.err.println("Scenario file is not a valid json")
      System.err.println(s"Errors found: ${err.msg}")
      sys.exit(2)
    }
    val validatedScenario = ProcessCanonizer.uncanonize(canonicalScenario)
    validatedScenario.valueOr { err =>
      System.err.println(s"Scenario uncanonization error: ${err.toList.mkString(", ")}")
      sys.exit(3)
    }
  }

  private def prepareScenarioInterpreter: KafkaTransactionalScenarioInterpreter = {
    // TODO Should it be extracted from designer?
    val engineConfig = ConfigFactory.load(ConfigFactory.parseFile(new File(System.getProperty("nussknacker.config.locations"))))
    val modelConfig: Config = engineConfig.getConfig("modelConfig")

    val modelData = ModelData(modelConfig, ModelClassLoader(modelConfig.as[List[URL]]("classPath")))
    // TODO Use correct MetricProvider
    val preparer = EngineRuntimeContextPreparer.forTest
    // TODO Pass correct ProcessVersion and DeploymentData
    val jobData = JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)

    val exceptionHandler = new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.error("Uncaught error occurred during scenario interpretation", e)
        sys.exit(5)
      }
    }
    new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData, preparer, exceptionHandler)
  }

}

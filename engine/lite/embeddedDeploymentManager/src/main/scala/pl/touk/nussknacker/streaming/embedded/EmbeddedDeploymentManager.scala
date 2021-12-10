package pl.touk.nussknacker.streaming.embedded

import akka.actor.ActorSystem
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.{DropwizardMetricsProviderFactory, LiteEngineMetrics}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, TypeSpecificInitialData}
import sttp.client.{NothingT, SttpBackend}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EmbeddedDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: ModelData, engineConfig: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager = {
    new EmbeddedDeploymentManager(modelData, engineConfig, EmbeddedDeploymentManager.logUnexpectedException)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def typeSpecificInitialData: TypeSpecificInitialData = TypeSpecificInitialData(StreamMetaData(Some(1)))

  override def supportsSignals: Boolean = false

  override def name: String = "lite-streaming-embedded"
}

object EmbeddedDeploymentManager extends LazyLogging {

  private[embedded] def logUnexpectedException(version: ProcessVersion, throwable: Throwable): Unit =
    logger.error(s"Scenario: $version failed unexpectedly", throwable)

}

/*
  Currently we assume that all operations that modify state (i.e. deploy and cancel) are performed from
  ManagementActor, which provides synchronization. Hence, we ignore all synchronization issues, except for
  checking status, but for this @volatile on interpreters should suffice.
 */
class EmbeddedDeploymentManager(modelData: ModelData, engineConfig: Config,
                                handleUnexpectedError: (ProcessVersion, Throwable) => Unit)(implicit ec: ExecutionContext) extends BaseDeploymentManager with LazyLogging {

  private val metricRegistry = LiteEngineMetrics.prepareRegistry(engineConfig)

  private val contextPreparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

  @volatile private var interpreters = Map[ProcessName, ScenarioInterpretationData]()

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    parseScenario(processDeploymentData).map { scenario =>
      val jobData = JobData(scenario.metaData, processVersion, deploymentData)

      interpreters.get(processVersion.processName).foreach { case ScenarioInterpretationData(_, processVersion, oldVersion) =>
        oldVersion.close()
        logger.debug(s"Closed already deployed scenario: $processVersion")
      }
      val interpreter = new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData, contextPreparer)
      val result = interpreter.run()
      result.onComplete {
        case Failure(exception) => handleUnexpectedError(processVersion, exception)
        case Success(_) => //closed without problems
      }

      val deploymentId = UUID.randomUUID().toString
      interpreters += (processVersion.processName -> ScenarioInterpretationData(deploymentId, processVersion, interpreter))
      logger.debug(s"Deployed scenario $processVersion")
      Some(ExternalDeploymentId(deploymentId))
    }
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    interpreters.get(name) match {
      case None => Future.failed(new IllegalArgumentException(s"Cannot find scenario $name"))
      case Some(ScenarioInterpretationData(_, _, interpreter)) => Future.successful {
        interpreters -= name
        interpreter.close()
        logger.debug(s"Scenario $name stopped")
      }
    }
  }

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful {
    interpreters.get(name).map { interpreterData =>
      ProcessState(interpreterData.deploymentId, SimpleStateStatus.Running,
        Some(interpreterData.processVersion), processStateDefinitionManager)
    }
  }

  override def close(): Unit = {
    interpreters.values.foreach(_.scenarioInterpreter.close())
    logger.info("All embedded scenarios successfully closed")
  }

  override def test[T](name: ProcessName, processJson: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = {
    Future{
      modelData.withThisAsContextClassLoader {
        val espProcess = ScenarioParser.parseUnsafe(processJson)
        KafkaTransactionalScenarioInterpreter.testRunner.runTest(modelData, testData, espProcess, variableEncoder)
      }
    }
  }

  private def parseScenario(processDeploymentData: ProcessDeploymentData): Future[EspProcess] = {
    processDeploymentData match {
      case GraphProcess(processAsJson) => ScenarioParser.parse(processAsJson) match {
        case Valid(a) => Future.successful(a)
        case Invalid(e) => Future.failed(new IllegalArgumentException(s"Failed to parse scenario: $e"))
      }
      case other => Future.failed(new IllegalArgumentException(s"Cannot deploy ${other.getClass.getName} in EmbeddedDeploymentManager"))
    }
  }

  case class ScenarioInterpretationData(deploymentId: String, processVersion: ProcessVersion, scenarioInterpreter: KafkaTransactionalScenarioInterpreter)
}


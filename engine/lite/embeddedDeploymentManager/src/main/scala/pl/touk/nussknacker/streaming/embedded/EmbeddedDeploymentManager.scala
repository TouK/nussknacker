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
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.kafka.TaskStatus.TaskStatus
import pl.touk.nussknacker.engine.lite.kafka.{KafkaTransactionalScenarioInterpreter, TaskStatus}
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.{DropwizardMetricsProviderFactory, LiteMetricRegistryFactory}
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, TypeSpecificInitialData}
import sttp.client.{NothingT, SttpBackend}

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class EmbeddedDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: ModelData, engineConfig: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager = {
    new EmbeddedDeploymentManager(modelData, engineConfig, deploymentService, EmbeddedDeploymentManager.logUnexpectedException)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def typeSpecificInitialData: TypeSpecificInitialData = TypeSpecificInitialData(LiteStreamMetaData(Some(1)))

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
                                processingTypeDeploymentService: ProcessingTypeDeploymentService,
                                handleUnexpectedError: (ProcessVersion, Throwable) => Unit)(implicit ec: ExecutionContext) extends BaseDeploymentManager with LazyLogging {

  private val retrieveDeployedScenariosTimeout = 10.seconds

  // TODO: better would be to use some global instance id - be default it could be instance id of designer (hostname:port by default)
  private val metricRegistry = LiteMetricRegistryFactory.usingHostnameAsDefaultInstanceId.prepareRegistry(engineConfig)

  private val contextPreparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

  override def processStateDefinitionManager: ProcessStateDefinitionManager = EmbeddedProcessStateDefinitionManager

  @volatile private var interpreters: Map[ProcessName, ScenarioInterpretationData] = {
    val deployedScenarios = Await.result(processingTypeDeploymentService.getDeployedScenarios, retrieveDeployedScenariosTimeout)
    deployedScenarios.map(data => deployScenario(data.processVersion, data.deploymentData, data.resolvedScenario, throwInterpreterRunExceptionsImmediately = false)._2).toMap
  }

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, graphProcess: GraphProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    parseScenario(graphProcess).map { parsedResolvedScenario =>
      deployScenarioClosingOldIfNeeded(processVersion, deploymentData, parsedResolvedScenario, throwInterpreterRunExceptionsImmediately = true)
    }
  }

  private def deployScenarioClosingOldIfNeeded(processVersion: ProcessVersion, deploymentData: DeploymentData,
                                               parsedResolvedScenario: EspProcess, throwInterpreterRunExceptionsImmediately: Boolean): Option[ExternalDeploymentId] = {
    interpreters.get(processVersion.processName).collect { case ScenarioInterpretationData(_, processVersion, Success(oldVersion)) =>
      oldVersion.close()
      logger.debug(s"Closed already deployed scenario: $processVersion")
    }
    val (deploymentId: String, deploymentEntry: (ProcessName, ScenarioInterpretationData)) = deployScenario(processVersion, deploymentData, parsedResolvedScenario, throwInterpreterRunExceptionsImmediately)
    interpreters += deploymentEntry
    Some(ExternalDeploymentId(deploymentId))
  }

  private def deployScenario(processVersion: ProcessVersion, deploymentData: DeploymentData,
                             parsedResolvedScenario: EspProcess, throwInterpreterRunExceptionsImmediately: Boolean) = {

    val interpreterTry = runInterpreter(processVersion, deploymentData, parsedResolvedScenario)
    interpreterTry match {
      case Failure(ex) if throwInterpreterRunExceptionsImmediately =>
        throw ex
      case Failure(ex) =>
        logger.error("Exception during deploy scenario. Scenario will be in Failed state", ex)
      case Success(_) =>
        logger.debug(s"Deployed scenario $processVersion")
    }
    val deploymentId = UUID.randomUUID().toString
    val deploymentEntry = processVersion.processName -> ScenarioInterpretationData(deploymentId, processVersion, interpreterTry)
    (deploymentId, deploymentEntry)
  }

  private def runInterpreter(processVersion: ProcessVersion, deploymentData: DeploymentData, parsedResolvedScenario: EspProcess) = {
    val jobData = JobData(parsedResolvedScenario.metaData, processVersion, deploymentData)
    val interpreterTry = Try(KafkaTransactionalScenarioInterpreter(parsedResolvedScenario, jobData, modelData, contextPreparer))
    interpreterTry.flatMap { interpreter =>
      val runTry = Try {
        val result = interpreter.run()
        result.onComplete {
          case Failure(exception) => handleUnexpectedError(processVersion, exception)
          case Success(_) => //closed without problems
        }
      }
      runTry.transform(
        _ => Success(interpreter),
        ex => {
          interpreter.close()
          Failure(ex)
        })
    }
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    interpreters.get(name) match {
      case None => Future.failed(new IllegalArgumentException(s"Cannot find scenario $name"))
      case Some(ScenarioInterpretationData(_, _, interpreterTry)) => Future.successful {
        interpreters -= name
        interpreterTry.foreach { interpreter =>
          interpreter.close()
          logger.debug(s"Scenario $name stopped")
        }
      }
    }
  }

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful {
    interpreters.get(name).map { interpreterData =>
      ProcessState(
        deploymentId = interpreterData.deploymentId,
        status = toScenarioStateStatus(interpreterData.scenarioInterpreter.map(_.status())),
        version = Some(interpreterData.processVersion),
        definitionManager = processStateDefinitionManager
      )
    }
  }

  private def toScenarioStateStatus(taskStatusTry: Try[TaskStatus]): StateStatus = taskStatusTry match {
    case Failure(ex) => EmbeddedStateStatus.failed(ex)
    case Success(TaskStatus.Running) => SimpleStateStatus.Running
    case Success(TaskStatus.DuringDeploy) => SimpleStateStatus.DuringDeploy
    case Success(TaskStatus.Restarting) => EmbeddedStateStatus.Restarting
    case Success(other) => throw new IllegalStateException(s"Not supporter task status: $other")
  }

  override def close(): Unit = {
    interpreters.values.foreach(_.scenarioInterpreter.foreach(_.close()))
    logger.info("All embedded scenarios successfully closed")
  }

  override def test[T](name: ProcessName, graphProcess: GraphProcess, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = {
    Future{
      modelData.withThisAsContextClassLoader {
        val espProcess = ScenarioParser.parseUnsafe(graphProcess)
        KafkaTransactionalScenarioInterpreter.testRunner.runTest(modelData, testData, espProcess, variableEncoder)
      }
    }
  }

  private def parseScenario(graphProcess: GraphProcess): Future[EspProcess] =
    ScenarioParser.parse(graphProcess) match {
      case Valid(a) => Future.successful(a)
      case Invalid(e) => Future.failed(new IllegalArgumentException(s"Failed to parse scenario: $e"))
    }

  case class ScenarioInterpretationData(deploymentId: String, processVersion: ProcessVersion, scenarioInterpreter: Try[KafkaTransactionalScenarioInterpreter])
}


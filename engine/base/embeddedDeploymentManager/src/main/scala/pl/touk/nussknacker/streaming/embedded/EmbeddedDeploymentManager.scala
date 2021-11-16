package pl.touk.nussknacker.streaming.embedded

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.EngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.baseengine.kafka.KafkaTransactionalScenarioInterpreter
import pl.touk.nussknacker.engine.baseengine.metrics.dropwizard.{BaseEngineMetrics, DropwizardMetricsProviderFactory}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, TypeSpecificInitialData}
import pl.touk.nussknacker.streaming.embedded.EmbeddedDeploymentManager.loggingExceptionHandler
import sttp.client.{NothingT, SttpBackend}

import java.lang.Thread.UncaughtExceptionHandler
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class EmbeddedDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: ModelData, engineConfig: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager = {
    new EmbeddedDeploymentManager(modelData, engineConfig)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def typeSpecificInitialData: TypeSpecificInitialData = TypeSpecificInitialData(StreamMetaData(Some(1)))

  override def supportsSignals: Boolean = false

  override def name: String = "nu-streaming-embedded"
}

object EmbeddedDeploymentManager {

  val loggingExceptionHandler: UncaughtExceptionHandler = new UncaughtExceptionHandler with LazyLogging {
    override def uncaughtException(t: Thread, e: Throwable): Unit = {
      logger.error("Uncaught error occurred during scenario interpretation", e)
    }
  }

}

class EmbeddedDeploymentManager(modelData: ModelData, engineConfig: Config,
                                exceptionHandler: UncaughtExceptionHandler = loggingExceptionHandler)(implicit ec: ExecutionContext) extends BaseDeploymentManager with LazyLogging {

  case class ScenarioInterpretationData(deploymentId: String, processVersion: ProcessVersion, scenarioInterpreter: KafkaTransactionalScenarioInterpreter)

  private val metricRegistry = BaseEngineMetrics.prepareRegistry(engineConfig)

  private val contextPreparer = new EngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

  private var interpreters = Map[ProcessName, ScenarioInterpretationData]()

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = Future.successful {
    val scenarioJson = processDeploymentData.asInstanceOf[GraphProcess].processAsJson
    val scenario = ProcessMarshaller.fromJson(scenarioJson)
      .andThen(ProcessCanonizer.uncanonize)
      .getOrElse(throw new IllegalArgumentException("Failed to parse"))

    val jobData = JobData(scenario.metaData, processVersion, deploymentData)

    interpreters.get(processVersion.processName).foreach { case ScenarioInterpretationData(_, processVersion, oldVersion) =>
      oldVersion.close()
      logger.debug(s"Closed already deployed scenario $processVersion")
    }

    val interpreter = new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData, contextPreparer, exceptionHandler)
    interpreter.run()
    val deploymentId = UUID.randomUUID().toString
    interpreters += (processVersion.processName -> ScenarioInterpretationData(deploymentId, processVersion, interpreter))
    Some(ExternalDeploymentId(deploymentId))
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    interpreters.get(name) match {
      case None => Future.failed(new Exception(s"Cannot find $name"))
      case Some(ScenarioInterpretationData(_, _, interpreter)) => Future.successful {
        interpreters -= name
        interpreter.close()
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
  }

  override def test[T](name: ProcessName, json: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = Future.failed(new IllegalArgumentException("Not supported yet"))

}

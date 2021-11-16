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
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, TypeSpecificDataInitializer}
import pl.touk.nussknacker.streaming.embedded.EmbeddedDeploymentManager.loggingExceptionHandler
import sttp.client.{NothingT, SttpBackend}

import java.lang.Thread.UncaughtExceptionHandler
import scala.concurrent.{ExecutionContext, Future}

class EmbeddedDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: ModelData, engineConfig: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager = {
    new EmbeddedDeploymentManager(modelData, engineConfig)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def typeSpecificDataInitializer: TypeSpecificDataInitializer = new TypeSpecificDataInitializer {
    override def forScenario: ScenarioSpecificData = StreamMetaData()

    override def forFragment: FragmentSpecificData = FragmentSpecificData()
  }

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

  private val metricRegistry = BaseEngineMetrics.prepareRegistry(engineConfig)

  private val contextPreparer = new EngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

  private var interpreters = Map[ProcessName, KafkaTransactionalScenarioInterpreter]()

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = Future.successful {
    val scenarioJson = processDeploymentData.asInstanceOf[GraphProcess].processAsJson
    val scenario = ProcessMarshaller.fromJson(scenarioJson)
      .andThen(ProcessCanonizer.uncanonize)
      .getOrElse(throw new IllegalArgumentException("Failed to parse"))

    val jobData = JobData(scenario.metaData, processVersion, deploymentData)

    val interpreter = new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData, contextPreparer, exceptionHandler)
    interpreter.run()

    interpreters += (processVersion.processName -> interpreter)
    None
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    interpreters.get(name) match {
      case None => Future.failed(new Exception(s"Cannot find $name"))
      case Some(interpreter) => Future.successful {
        interpreters -= name
        interpreter.close()
      }
    }
  }

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful {
    interpreters.get(name).map { interpreter =>
      ProcessState("???", SimpleStateStatus.Running,
        Some(interpreter.jobData.processVersion), processStateDefinitionManager)
    }
  }

  override def close(): Unit = {
    interpreters.values.foreach(_.close())
  }

  override def test[T](name: ProcessName, json: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = Future.failed(new IllegalArgumentException("Not supported"))


}

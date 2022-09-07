package pl.touk.nussknacker.engine.lite.app

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, LiteStreamMetaData, ProcessVersion, RequestResponseMetaData}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.RunnableScenarioInterpreter
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.kafka.{KafkaTransactionalScenarioInterpreter, LiteKafkaJobData}
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.{DropwizardMetricsProviderFactory, LiteMetricRegistryFactory}
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus
import pl.touk.nussknacker.engine.requestresponse.{RequestResponseConfig, RequestResponseRunnableScenarioInterpreter}

import scala.concurrent.Future
import java.net.URL

object RunnableScenarioInterpreterFactory extends LazyLogging {

  def prepareScenarioInterpreter(scenario: EspProcess, runtimeConfig: Config, deploymentConfig: Config, system: ActorSystem): RunnableScenarioInterpreter = {
    val modelConfig: Config = runtimeConfig.getConfig("modelConfig")
    val modelData = ModelData(modelConfig, ModelClassLoader(modelConfig.as[List[URL]]("classPath")))
    val metricRegistry = prepareMetricRegistry(runtimeConfig)
    val preparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))
    // TODO Pass correct ProcessVersion and DeploymentData
    val jobData = JobData(scenario.metaData, ProcessVersion.empty)

    prepareScenarioInterpreter(scenario, runtimeConfig, jobData, deploymentConfig, modelData, preparer)(system)
  }

  private def prepareScenarioInterpreter(scenario: EspProcess, runtimeConfig: Config, jobData: JobData, deploymentConfig: Config,
                                         modelData: ModelData, preparer: LiteEngineRuntimeContextPreparer)(implicit system: ActorSystem) = {
    import system.dispatcher
    scenario.metaData.typeSpecificData match {
      case _: LiteStreamMetaData =>
        KafkaTransactionalScenarioInterpreter(scenario, jobData, deploymentConfig.as[LiteKafkaJobData], modelData, preparer)
      case _: RequestResponseMetaData =>
        val requestResponseConfig = runtimeConfig.as[RequestResponseConfig]("request-response")
        new RequestResponseRunnableScenarioInterpreter(jobData, scenario, modelData, preparer, requestResponseConfig)
      case other =>
        throw new IllegalArgumentException("Not supported scenario meta data type: " + other)
    }
  }

  private def prepareMetricRegistry(engineConfig: Config) = {
    lazy val instanceId = sys.env.getOrElse("INSTANCE_ID", LiteMetricRegistryFactory.hostname)
    new LiteMetricRegistryFactory(instanceId).prepareRegistry(engineConfig)
  }

}
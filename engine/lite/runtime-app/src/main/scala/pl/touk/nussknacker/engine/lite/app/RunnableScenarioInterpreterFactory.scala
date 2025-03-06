package pl.touk.nussknacker.engine.lite.app

import akka.actor.ActorSystem
import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import pl.touk.nussknacker.engine.{ModelConfigs, ModelData}
import pl.touk.nussknacker.engine.api.{JobData, LiteStreamMetaData, ProcessVersion, RequestResponseMetaData}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.namespaces.Namespace
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.RunnableScenarioInterpreter
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.kafka.{KafkaTransactionalScenarioInterpreter, LiteKafkaJobData}
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.{DropwizardMetricsProviderFactory, LiteMetricRegistryFactory}
import pl.touk.nussknacker.engine.requestresponse.{RequestResponseConfig, RequestResponseRunnableScenarioInterpreter}
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.engine.util.loader.{DeploymentManagersClassLoader, ModelClassLoader}

object RunnableScenarioInterpreterFactory extends LazyLogging {

  def prepareScenarioInterpreter(
      scenario: CanonicalProcess,
      runtimeConfig: Config,
      deploymentData: LiteDeploymentData,
      system: ActorSystem
  ): Resource[IO, RunnableScenarioInterpreter] = {
    for {
      deploymentManagersClassLoader <- DeploymentManagersClassLoader.create(List.empty)
      scenarioInterpreter <- Resource
        .make(
          acquire = IO.delay {
            val modelConfig = runtimeConfig.getConfig("modelConfig")
            val urls        = modelConfig.as[List[String]]("classPath")
            val modelData = ModelData.duringExecution(
              ModelConfigs(modelConfig),
              ModelClassLoader(urls, workingDirectoryOpt = None, deploymentManagersClassLoader),
              resolveConfigs = true
            )
            val metricRegistry = prepareMetricRegistry(runtimeConfig, modelData.namingStrategy.namespace)
            val preparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))
            val jobData  = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))

            prepareScenarioInterpreter(
              scenario,
              runtimeConfig,
              jobData,
              deploymentData,
              modelData,
              preparer
            )(system)
          }
        )(
          release = scenarioInterpreter => IO.delay(scenarioInterpreter.close())
        )
    } yield scenarioInterpreter
  }

  private def prepareScenarioInterpreter(
      scenario: CanonicalProcess,
      runtimeConfig: Config,
      jobData: JobData,
      deploymentData: LiteDeploymentData,
      modelData: ModelData,
      preparer: LiteEngineRuntimeContextPreparer
  )(implicit system: ActorSystem) = {
    import system.dispatcher
    scenario.metaData.typeSpecificData match {
      case _: LiteStreamMetaData =>
        KafkaTransactionalScenarioInterpreter(
          modelData,
          preparer,
          scenario,
          jobData,
          deploymentData.nodesData,
          LiteKafkaJobData(deploymentData.tasksCountUnsafe),
        )
      case _: RequestResponseMetaData =>
        val requestResponseConfig = runtimeConfig.as[RequestResponseConfig]("request-response")
        new RequestResponseRunnableScenarioInterpreter(
          modelData,
          preparer,
          scenario,
          jobData,
          deploymentData.nodesData,
          requestResponseConfig
        )
      case other =>
        throw new IllegalArgumentException("Not supported scenario meta data type: " + other)
    }
  }

  private def prepareMetricRegistry(engineConfig: Config, namespace: Option[Namespace]) = {
    lazy val instanceId = sys.env.getOrElse("INSTANCE_ID", LiteMetricRegistryFactory.hostname)
    new LiteMetricRegistryFactory(instanceId, namespace).prepareRegistry(engineConfig)
  }

}

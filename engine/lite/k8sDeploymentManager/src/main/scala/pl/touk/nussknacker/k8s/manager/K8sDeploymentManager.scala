package pl.touk.nussknacker.k8s.manager

import akka.http.scaladsl.settings.ConnectionPoolSettings
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax._
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager._
import pl.touk.nussknacker.k8s.manager.K8sUtils.{sanitizeLabel, sanitizeObjectName, shortHash}
import pl.touk.nussknacker.k8s.manager.deployment._
import pl.touk.nussknacker.k8s.manager.deployment.K8sScalingConfig.DividingParallelismConfig
import pl.touk.nussknacker.k8s.manager.ingress.IngressPreparer
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer
import pl.touk.nussknacker.lite.manager.LiteDeploymentManager
import skuber.{
  ConfigMap,
  LabelSelector,
  ListResource,
  ObjectMeta,
  ObjectResource,
  Pod,
  ResourceQuotaList,
  Secret,
  Service
}
import skuber.LabelSelector.Requirement
import skuber.LabelSelector.dsl._
import skuber.api.Configuration
import skuber.api.client.{KubernetesClient, LoggingConfig}
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.networking.v1.Ingress

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.language.reflectiveCalls
import scala.util.Using

class K8sDeploymentManager(
    override protected val modelData: BaseModelData,
    config: K8sDeploymentManagerConfig,
    rawConfig: Config,
    dependencies: DeploymentManagerDependencies
) extends LiteDeploymentManager
    with LazyLogging {

  import dependencies._

  // lazy initialization to allow starting application even if k8s is not available - e.g. in case if multiple DeploymentManagers configured
  private lazy val k8sClient =
    createK8sClient(effectiveSkuberAppConfig, ConnectionPoolSettings(effectiveSkuberAppConfig))

  private lazy val scenarioStateK8sClient = createK8sClient(
    effectiveSkuberAppConfig,
    ConnectionPoolSettings(effectiveSkuberAppConfig).withUpdatedConnectionSettings(
      _.withIdleTimeout(config.scenarioStateIdleTimeout)
    )
  )

  private def createK8sClient(
      skuberAppConfig: Config,
      connectionPoolSettings: ConnectionPoolSettings
  ): KubernetesClient = {
    skuber.api.client.init(
      k8sConfiguration.currentContext,
      LoggingConfig(),
      None,
      skuberAppConfig,
      Some(connectionPoolSettings)
    )
  }

  protected def k8sConfiguration: Configuration = {
    Configuration.defaultK8sConfig
  }

  // We pass rawConfig and compute this effective skuber app config to make sure that skuber doesn't use
  // root level ActorSystem configuration
  private val effectiveSkuberAppConfig = rawConfig.withFallback(ConfigFactory.defaultReference(getClass.getClassLoader))

  private lazy val k8sUtils               = new K8sUtils(k8sClient)
  private val deploymentPreparer          = new DeploymentPreparer(config)
  private val servicePreparer             = new ServicePreparer(config)
  private val scalingOptionsDeterminerOpt = K8sScalingOptionsDeterminer.create(config.scalingConfig)
  private val ingressPreparerOpt          = config.ingress.map(new IngressPreparer(_, config.nussknackerInstanceName))

  // runtime config is combined from scenarioType.modelConfig and deploymentConfig.configExecutionOverrides
  private val serializedRuntimeConfig = {
    val inputConfig     = modelData.inputConfigDuringExecution
    val modelConfigPart = inputConfig.config.withoutPath("classPath").atPath("modelConfig")
    // TODO: should overrides apply only to model or to whole config??
    val withOverrides = config.configExecutionOverrides.withFallback(modelConfigPart)
    inputConfig.copy(config = withOverrides).serialized
  }

  private lazy val defaultLogbackConfig = Using.resource(Source.fromResource("runtime/default-logback.xml"))(_.mkString)

  private def logbackConfig: String = config.logbackConfigPath
    .map(path => Using.resource(Source.fromFile(path))(_.mkString))
    .getOrElse(defaultLogbackConfig)

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] =
    command match {
      case command: DMValidateScenarioCommand => validateScenario(command)
      case command: DMRunDeploymentCommand    => runDeployment(command)
      case command: DMCancelScenarioCommand   => cancelScenario(command)
      case command: DMTestScenarioCommand     => testScenario(command)
      case _: DMCancelDeploymentCommand | _: DMStopDeploymentCommand | _: DMStopScenarioCommand |
          _: DMMakeScenarioSavepointCommand | _: DMRunOffScheduleCommand =>
        notImplemented
    }

  private def validateScenario(command: DMValidateScenarioCommand): Future[Unit] = {
    import command._
    val scalingOptions = determineScalingOptions(canonicalProcess)
    val deploymentStrategy = deploymentPreparer
      .prepare(
        processVersion,
        canonicalProcess.metaData.typeSpecificData,
        MountableResources("dummy", "dummy", "dummy"),
        scalingOptions.replicasCount
      )
      .spec
      .flatMap(_.strategy)
    for {
      resourceQuotas <- k8sClient.list[ResourceQuotaList]()
      oldDeployment <- updateStrategy match {
        case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(_) =>
          k8sClient.getOption[Deployment](
            objectNameForScenario(processVersion, config.nussknackerInstanceName, None)
          )
        case DeploymentUpdateStrategy.DontReplaceDeployment =>
          throw new IllegalArgumentException(s"Deployment update strategy: $updateStrategy is not supported")
      }
      _ <- Future.fromTry(
        K8sPodsResourceQuotaChecker
          .hasReachedQuotaLimit(
            oldDeployment.flatMap(_.spec.flatMap(_.replicas)),
            resourceQuotas,
            scalingOptions.replicasCount,
            deploymentStrategy
          )
          .toEither
          .toTry
      )
      _ <- validatePreparedService(processVersion, canonicalProcess.metaData)
    } yield ()
  }

  private def runDeployment(command: DMRunDeploymentCommand): Future[Option[ExternalDeploymentId]] = {
    import command._
    val scalingOptions = determineScalingOptions(canonicalProcess)
    logger.debug(s"Deploying $processVersion")
    for {
      _ <- Future {
        updateStrategy match {
          case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(_) => ()
          case DeploymentUpdateStrategy.DontReplaceDeployment =>
            throw new IllegalArgumentException(s"Deployment update strategy: $updateStrategy is not supported")
        }
      }
      configMap <- k8sUtils.createOrUpdate(
        configMapForData(processVersion, canonicalProcess, config.nussknackerInstanceName)(
          Map(
            "scenario.json" -> canonicalProcess.asJson.noSpaces,
            "deploymentConfig.conf" -> ConfigFactory
              .empty()
              .withValue("tasksCount", fromAnyRef(scalingOptions.noOfTasksInReplica))
              .root()
              .render()
          )
        )
      )
      loggingConfigMap <- k8sUtils.createOrUpdate(
        configMapForData(processVersion, canonicalProcess, config.nussknackerInstanceName)(
          Map("logback.xml" -> logbackConfig),
          additionalLabels = Map(resourceTypeLabel -> "logging-conf"),
          overrideName = config.commonConfigMapForLogback
        )
      )
      // runtimeConfig.conf often contains confidential data e.g passwords, so we put it in secret, not configmap
      secret <- k8sUtils.createOrUpdate(
        secretForData(processVersion, canonicalProcess, config.nussknackerInstanceName)(
          Map("runtimeConfig.conf" -> serializedRuntimeConfig)
        )
      )
      mountableResources = MountableResources(
        commonConfigConfigMap = configMap.name,
        loggingConfigConfigMap = loggingConfigMap.name,
        runtimeConfigSecret = secret.name
      )
      deployment <- k8sUtils.createOrUpdate(
        deploymentPreparer.prepare(
          processVersion,
          canonicalProcess.metaData.typeSpecificData,
          mountableResources,
          scalingOptions.replicasCount
        )
      )
      serviceOpt <- servicePreparer
        .prepare(processVersion, canonicalProcess.metaData)
        .map(k8sUtils.createOrUpdate[Service](_).map(Some(_)))
        .getOrElse(Future.successful(None))
      ingressOpt <- serviceOpt
        .flatMap(s =>
          ingressPreparerOpt.flatMap(
            _.prepare(processVersion, canonicalProcess.metaData.typeSpecificData, s.name, config.servicePort)
          )
        )
        .map(k8sUtils.createOrUpdate[Ingress](_).map(Some(_)))
        .getOrElse(Future.successful(None))
      // we don't wait until deployment succeeds before deleting old maps and service, but for now we don't rollback anyway
      // https://github.com/kubernetes/kubernetes/issues/22368#issuecomment-790794753
      _ <- k8sClient.deleteAllSelected[ListResource[ConfigMap]](
        LabelSelector(
          requirementForId(processVersion.processId),
          configMapIdLabel isNotIn List(configMap, loggingConfigMap).map(_.name)
        )
      )
      _ <- k8sClient.deleteAllSelected[ListResource[Secret]](
        LabelSelector(
          requirementForId(processVersion.processId),
          secretIdLabel isNot secret.name
        )
      )
      // cleaning up after after possible slug change
      _ <- k8sClient.deleteAllSelected[ListResource[Service]](
        LabelSelector(
          requirementForId(processVersion.processId),
          scenarioVersionLabel isNot processVersion.versionId.value.toString
        )
      )
    } yield {
      logger.info(
        s"Deployed ${processVersion.processName}, with deployment: ${deployment.name}, configmap: ${configMap.name}${serviceOpt
            .map(svc => s", service: ${svc.name}")
            .getOrElse("")}${ingressOpt.map(i => s", ingress: ${i.name}").getOrElse("")}"
      )
      logger.trace(s"K8s deployment name: ${deployment.name}: K8sDeploymentConfig: ${config.k8sDeploymentConfig}")
      None
    }
  }

  private def determineScalingOptions(canonicalProcess: CanonicalProcess) = {
    canonicalProcess.metaData.typeSpecificData match {
      case stream: LiteStreamMetaData =>
        val determiner = scalingOptionsDeterminerOpt.getOrElse(defaultKafkaScalingDeterminer)
        determiner.determine(stream.parallelism.getOrElse(defaultParallelism))
      case _: RequestResponseMetaData =>
        val replicasCount = scalingOptionsDeterminerOpt match {
          case Some(fixed: FixedReplicasCountK8sScalingOptionsDeterminer) =>
            fixed.replicasCount
          case Some(_) =>
            logger.debug(
              s"Not supported scaling config for request-response scenario type: ${config.scalingConfig}. Will be used default replicase count: $defaultReqRespReplicaseCount instead"
            )
            defaultReqRespReplicaseCount
          case None =>
            defaultReqRespReplicaseCount
        }
        // TODO: noOfTasksInReplica currently has no effect in req-resp, maybe we would like to have some options to tune http throughput instead?
        K8sScalingOptions(replicasCount = replicasCount, noOfTasksInReplica = 1)
      case other =>
        throw new IllegalArgumentException("Not supported scenario meta data type: " + other)
    }
  }

  private def validatePreparedService(processVersion: ProcessVersion, metaData: MetaData): Future[Unit] = {
    servicePreparer.prepare(processVersion, metaData) match {
      case None =>
        Future.successful(())
      case Some(service) =>
        k8sClient.getOption[Service](service.name).flatMap {
          case None => Future.successful(())
          case Some(existing) =>
            parseVersionAnnotation(existing) match {
              case Some(existingServiceData) if existingServiceData.processId != processVersion.processId =>
                val message = s"Slug is not unique, scenario ${existingServiceData.processName} is using it"
                Future.failed(new IllegalArgumentException(message))
              case _ => Future.successful(())
            }

        }
    }
  }

  private def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = {
    import command._
    // TODO: move to requirementForId when cancel changes the API...
    val selector: LabelSelector = requirementForName(scenarioName)
    // We wait for deployment removal before removing configmaps,
    // in case of crash it's better to have unnecessary configmaps than deployments without config
    for {
      ingresses <-
        if (config.ingress.exists(_.enabled)) k8sClient.deleteAllSelected[ListResource[Ingress]](selector).map(Some(_))
        else Future.successful(None)
      // we split into two steps because of missing k8s svc deletecollection feature in version <= 1.22
      services    <- k8sClient.listSelected[ListResource[Service]](selector)
      _           <- Future.sequence(services.map(s => k8sClient.delete[Service](s.name)))
      deployments <- k8sClient.deleteAllSelected[ListResource[Deployment]](selector)
      configMaps  <- k8sClient.deleteAllSelected[ListResource[ConfigMap]](selector)
      secrets     <- k8sClient.deleteAllSelected[ListResource[Secret]](selector)
    } yield {
      logger.debug(
        s"Canceled $scenarioName, ${ingresses.map(i => s"ingresses: ${i.itemNames}, ").getOrElse("")}services: ${services.itemNames}, deployments: ${deployments.itemNames}, configmaps: ${configMaps.itemNames}, secrets: ${secrets.itemNames}"
      )
      ()
    }
  }

  override def getScenarioDeploymentsStatuses(
      scenarioName: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] = {
    for {
      deployments <- scenarioStateK8sClient
        .listSelected[ListResource[Deployment]](requirementForName(scenarioName))
        .map(_.items)
      pods <- scenarioStateK8sClient.listSelected[ListResource[Pod]](requirementForName(scenarioName)).map(_.items)
    } yield {
      WithDataFreshnessStatus.fresh(deployments.map(K8sDeploymentStatusMapper.status(_, pods)))
    }
  }

  private def configMapForData(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess,
      nussknackerInstanceName: Option[String]
  )(
      data: Map[String, String],
      additionalLabels: Map[String, String] = Map.empty,
      overrideName: Option[String] = None
  ): ConfigMap = {
    val scenario = canonicalProcess.asJson.spaces2
    // we append serialized data to name so we can guarantee pods will be restarted.
    // They *probably* will restart anyway, as scenario version is in label, but e.g. if only model config is changed?
    val objectName = overrideName.getOrElse(
      objectNameForScenario(processVersion, config.nussknackerInstanceName, Some(scenario + data.toString()))
    )
    val identificationLabels: Map[String, String] = overrideName match {
      case Some(_) => Map.empty // when name is overridden, we do not want DeploymentManager to have control over whole
      // lifecycle of CM (e.g. delete it on scenario canceling)
      case None => labelsForScenario(processVersion, nussknackerInstanceName)
    }
    ConfigMap(
      metadata = ObjectMeta(
        name = objectName,
        labels = identificationLabels + (configMapIdLabel -> objectName) ++ additionalLabels
      ),
      data = data
    )
  }

  private def secretForData(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess,
      nussknackerInstanceName: Option[String]
  )(data: Map[String, String], additionalLabels: Map[String, String] = Map.empty): Secret = {
    val scenario = canonicalProcess.asJson.spaces2
    val objectName =
      objectNameForScenario(processVersion, config.nussknackerInstanceName, Some(scenario + data.toString()))
    Secret(
      metadata = ObjectMeta(
        name = objectName,
        labels =
          labelsForScenario(processVersion, nussknackerInstanceName) + (secretIdLabel -> objectName) ++ additionalLabels
      ),
      data = data.mapValuesNow(_.getBytes)
    )
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager = K8sProcessStateDefinitionManager

  override protected def executionContext: ExecutionContext = dependencies.executionContext

  // TODO We don't handle deployment synchronization on k8s DM because with current resources model it wasn't trivial to implement it.
  //      The design of resources is that each scenario has only one k8s deployment and we don't want to rollout this deployment when
  //      when nothing important is changed (e.g. deploymentId is changed). We should rethink if we want to handle multiple deployments
  //      for each scenario in this case and where store the deploymentId
  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  override def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
    NoDeploymentsStatusesQueryForAllScenariosSupport

  override def schedulingSupport: SchedulingSupport = NoSchedulingSupport
}

object K8sDeploymentManager {

  val defaultParallelism = 1

  // 4 because it is quite normal number of cpus reserved for one container
  val defaultKafkaTasksPerReplica = 4

  val defaultKafkaScalingDeterminer: K8sScalingOptionsDeterminer = new DividingParallelismK8sScalingOptionsDeterminer(
    DividingParallelismConfig(defaultKafkaTasksPerReplica)
  )

  // 2 for HA purpose
  val defaultReqRespReplicaseCount = 2

  val nussknackerInstanceNameLabel: String = "nussknacker.io/nussknackerInstanceName"

  val scenarioNameLabel: String = "nussknacker.io/scenarioName"

  val scenarioIdLabel: String = "nussknacker.io/scenarioId"

  val scenarioVersionLabel: String = "nussknacker.io/scenarioVersion"

  val scenarioVersionAnnotation: String = "nussknacker.io/scenarioVersion"

  val configMapIdLabel: String = "nussknacker.io/configMapId"

  val secretIdLabel: String = "nussknacker.io/secretId"

  val resourceTypeLabel: String = "nussknacker.io/resourceType"

  /*
    We use name label to find deployment to cancel/getProcessState, we append short hash to reduce collision risk
    if sanitized names are similar (e.g. they differ only in truncated part or non-alphanumeric characters
    TODO: Maybe it would be better to just pass ProcessId in getProcessState/cancel?
   */
  private[manager] def scenarioNameLabelValue(processName: ProcessName) = {
    val name = processName.value

    sanitizeLabel(name, s"-${shortHash(name)}")
  }

  private[manager] def requirementForName(processName: ProcessName): Requirement =
    scenarioNameLabel is scenarioNameLabelValue(processName)

  private[manager] def requirementForId(processId: ProcessId): Requirement = scenarioIdLabel is processId.value.toString

  /*
    Labels contain scenario name, scenario id and version.
   */
  private[manager] def labelsForScenario(processVersion: ProcessVersion, nussknackerInstanceName: Option[String]) = Map(
    scenarioNameLabel    -> scenarioNameLabelValue(processVersion.processName),
    scenarioIdLabel      -> processVersion.processId.value.toString,
    scenarioVersionLabel -> processVersion.versionId.value.toString
  ) ++ nussknackerInstanceName.map(nussknackerInstanceNameLabel -> _)

  private[manager] def versionAnnotationForScenario(processVersion: ProcessVersion) =
    Map(scenarioVersionAnnotation -> processVersion.asJson.spaces2)

  /*
    Id of both is created with scenario id + sanitized name. This is to:
    - guarantee uniqueness - id is sufficient for that, sanitized name - not necessarily, as replacement/shortening may lead to duplicates
      (other way to mitigate this would be to generate some hash, but it's a bit more complex...)
    - ensure some level of readability - only id would be hard to match name to scenario
   */
  private[manager] def objectNameForScenario(
      processVersion: ProcessVersion,
      nussknackerInstanceName: Option[String],
      hashInput: Option[String]
  ): String = {
    // we simulate (more or less) --append-hash kubectl behaviour...
    val hashToAppend      = hashInput.map(input => "-" + shortHash(input)).getOrElse("")
    val plainScenarioName = s"scenario-${processVersion.processId.value}-${processVersion.processName}"
    val scenarioName      = objectNamePrefixedWithNussknackerInstanceName(nussknackerInstanceName, plainScenarioName)
    sanitizeObjectName(scenarioName, hashToAppend)
  }

  private[manager] def objectNamePrefixedWithNussknackerInstanceName(
      nussknackerInstanceName: Option[String],
      objectName: String
  ) =
    sanitizeObjectName(
      objectNamePrefixedWithNussknackerInstanceNameWithoutSanitization(nussknackerInstanceName, objectName)
    )

  private[manager] def objectNamePrefixedWithNussknackerInstanceNameWithoutSanitization(
      nussknackerInstanceName: Option[String],
      objectName: String
  ) =
    nussknackerInstanceNamePrefix(nussknackerInstanceName) + objectName

  private[manager] def nussknackerInstanceNamePrefix(nussknackerInstanceName: Option[String]) =
    nussknackerInstanceName.map(_ + "-").getOrElse("")

  private[manager] def parseVersionAnnotation(deployment: ObjectResource): Option[ProcessVersion] = {
    deployment.metadata.annotations
      .get(scenarioVersionAnnotation)
      .flatMap(CirceUtil.decodeJson[ProcessVersion](_).toOption)
  }

}

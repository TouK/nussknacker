package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import monocle.Iso
import monocle.macros.GenLens
import monocle.std.option._
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, ProcessVersion, RequestResponseMetaData, TypeSpecificData}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.{
  labelsForScenario,
  objectNameForScenario,
  scenarioIdLabel,
  scenarioVersionAnnotation,
  versionAnnotationForScenario
}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManagerConfig
import skuber.{Container, EnvVar, HTTPGetAction, LabelSelector, Pod, Probe, Volume}
import skuber.EnvVar.FieldRef
import skuber.LabelSelector.IsEqualRequirement
import skuber.apps.v1.Deployment
import skuber.apps.v1.Deployment.Strategy

case class MountableResources(
    commonConfigConfigMap: String,
    loggingConfigConfigMap: String,
    runtimeConfigSecret: String
)

class DeploymentPreparer(config: K8sDeploymentManagerConfig) extends LazyLogging {

  private val CommonConfigMountPath  = "/config"
  private val LoggingConfigMountPath = "/logging-config"
  private val RuntimeConfigMountPath = "/runtime-config"

  def prepare(
      processVersion: ProcessVersion,
      typeSpecificData: TypeSpecificData,
      resourcesToMount: MountableResources,
      determinedReplicasCount: Int
  ): Deployment = {
    val userConfigurationBasedDeployment = DeploymentUtils.parseDeploymentWithFallback(
      config.k8sDeploymentConfig,
      getClass.getResource(s"/defaultMinimalDeployment.conf")
    )
    applyDeploymentDefaults(
      userConfigurationBasedDeployment,
      processVersion,
      typeSpecificData,
      resourcesToMount,
      determinedReplicasCount,
      config.nussknackerInstanceName
    )
  }

  private def applyDeploymentDefaults(
      userConfigurationBasedDeployment: Deployment,
      processVersion: ProcessVersion,
      typeSpecificData: TypeSpecificData,
      resourcesToMount: MountableResources,
      determinedReplicasCount: Int,
      nussknackerInstanceName: Option[String]
  ) = {
    val objectName  = objectNameForScenario(processVersion, config.nussknackerInstanceName, None)
    val annotations = versionAnnotationForScenario(processVersion)
    val labels      = labelsForScenario(processVersion, nussknackerInstanceName)

    // we use 'OptionOptics some' here and do not worry about withDefault because _.spec is provided in defaultMinimalDeployment
    val deploymentSpecLens = GenLens[Deployment](_.spec) composePrism some
    val templateSpecLens =
      deploymentSpecLens composeLens GenLens[Deployment.Spec](_.template.spec) composeIso withDefault(Pod.Spec())
    val deploymentLens =
      GenLens[Deployment](_.metadata.name).set(objectName) andThen
        GenLens[Deployment](_.metadata.labels).modify(_ ++ labels) andThen
        GenLens[Deployment](_.metadata.annotations).modify(_ ++ annotations) andThen
        // here we use id to avoid sanitization problems
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.selector))
          .set(LabelSelector(IsEqualRequirement(scenarioIdLabel, processVersion.processId.value.toString))) andThen
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.strategy)).modify(maybeStrategy =>
          maybeStrategy.orElse(Some(deploymentStrategy(typeSpecificData)))
        ) andThen
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.replicas))
          .modify(modifyReplicasCount(determinedReplicasCount)) andThen
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.template.metadata.name)).set(objectName) andThen
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.template.metadata.labels))
          .modify(_ ++ labels) andThen
        (templateSpecLens composeLens GenLens[Pod.Spec](_.volumes)).modify(
          _ ++ List(
            Volume("common-conf", Volume.ConfigMapVolumeSource(resourcesToMount.commonConfigConfigMap)),
            Volume("logging-conf", Volume.ConfigMapVolumeSource(resourcesToMount.loggingConfigConfigMap)),
            Volume("runtime-conf", Volume.Secret(resourcesToMount.runtimeConfigSecret)),
          )
        ) andThen
        (templateSpecLens composeLens GenLens[Pod.Spec](_.containers)).modify(containers =>
          modifyContainers(containers)
        )
    deploymentLens(userConfigurationBasedDeployment)
  }

  private def deploymentStrategy(typeSpecificData: TypeSpecificData): Strategy = {
    typeSpecificData match {
      case _: LiteStreamMetaData => Deployment.Strategy.Recreate
      case _: RequestResponseMetaData =>
        Deployment.Strategy(rollingUpdate =
          Deployment.RollingUpdate(maxUnavailable = Right("25%"), maxSurge = Right("25%"))
        )
      case other: TypeSpecificData => throw new IllegalStateException(s"Deploying $other unsupported on k8s")
    }
  }

  private def modifyReplicasCount(determinedReplicasCount: Int)(maybeReplicasFromRawConfig: Option[Int]) =
    maybeReplicasFromRawConfig match {
      case Some(replicasFromRawConfig) =>
        logger.warn(
          s"Found replicas specified in config: $replicasFromRawConfig that will be used instead of replicas determined based on scaling config: $determinedReplicasCount. It can cause not equal tasks assigment to replicas"
        )
        Some(replicasFromRawConfig)
      case None =>
        Some(determinedReplicasCount)
    }

  private def withDefault[A](defaultValue: A): Iso[Option[A], A] =
    Iso[Option[A], A](_.getOrElse(defaultValue))(value => if (value == defaultValue) None else Some(value))

  private def modifyContainers(containers: List[Container]): List[Container] = {
    val image                 = s"${config.dockerImageName}:${config.dockerImageTag}"
    val defaultTimeoutSeconds = 5
    val runtimeContainer = Container(
      name = "runtime",
      image = image,
      env = List(
        EnvVar("SCENARIO_FILE", s"$CommonConfigMountPath/scenario.json"),
        EnvVar("CONFIG_FILE", s"/opt/nussknacker/conf/application.conf,$RuntimeConfigMountPath/runtimeConfig.conf"),
        EnvVar("DEPLOYMENT_CONFIG_FILE", s"$CommonConfigMountPath/deploymentConfig.conf"),
        EnvVar("LOGBACK_FILE", s"$LoggingConfigMountPath/logback.xml"),
        // We pass POD_NAME, because there is no option to pass only replica hash which is appended to pod name.
        // Hash will be extracted on entrypoint side.
        EnvVar("POD_NAME", FieldRef("metadata.name"))
      ),
      volumeMounts = List(
        Volume.Mount(name = "common-conf", mountPath = CommonConfigMountPath),
        Volume.Mount(name = "logging-conf", mountPath = LoggingConfigMountPath),
        Volume.Mount(name = "runtime-conf", mountPath = RuntimeConfigMountPath),
      ),
      // used standard AkkaManagement see HealthCheckServerRunner for details
      startupProbe = Some(
        Probe(
          new HTTPGetAction(Left(8080), path = "/alive"),
          periodSeconds = Some(1),
          failureThreshold = Some(60),
          timeoutSeconds = defaultTimeoutSeconds
        )
      ),
      readinessProbe = Some(
        Probe(
          new HTTPGetAction(Left(8080), path = "/ready"),
          periodSeconds = Some(5),
          failureThreshold = Some(3),
          timeoutSeconds = defaultTimeoutSeconds
        )
      ),
      livenessProbe = Some(
        Probe(
          new HTTPGetAction(Left(8080), path = "/alive"),
          periodSeconds = Some(5),
          failureThreshold = Some(3),
          timeoutSeconds = defaultTimeoutSeconds
        )
      )
    )

    def modifyRuntimeContainer(value: Container): Container = {
      val containerLens = GenLens[Container](_.env).modify(_ ++ runtimeContainer.env) andThen
        GenLens[Container](_.volumeMounts).modify(_ ++ runtimeContainer.volumeMounts) andThen
        GenLens[Container](_.startupProbe).modify(_.orElse(runtimeContainer.startupProbe)) andThen
        GenLens[Container](_.readinessProbe).modify(_.orElse(runtimeContainer.readinessProbe)) andThen
        GenLens[Container](_.livenessProbe).modify(_.orElse(runtimeContainer.livenessProbe)) andThen
        GenLens[Container](_.image).modify { configuredImage =>
          if (configuredImage != runtimeContainer.image)
            logger.warn(s"Overriding $configuredImage image with ${runtimeContainer.image} image")
          runtimeContainer.image
        }
      containerLens(value)
    }

    val (runtimeContainers, nonRuntimeContainers) = containers.partition(_.name == "runtime")

    (runtimeContainers match {
      case Nil           => List(runtimeContainer)
      case single :: Nil => List(modifyRuntimeContainer(single))
      case _             => throw new IllegalStateException("Deployment should have only one 'runtime' container")
    }) ++ nonRuntimeContainers
  }

}

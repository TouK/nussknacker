package pl.touk.nussknacker.k8s.manager.deployment

import io.circe.syntax.EncoderOps
import monocle.Iso
import monocle.macros.GenLens
import monocle.std.option._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.{labelsForScenario, objectNameForScenario, scenarioIdLabel, scenarioVersionAnnotation}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManagerConfig
import skuber.EnvVar.FieldRef
import skuber.LabelSelector.IsEqualRequirement
import skuber.apps.v1.Deployment
import skuber.{Container, EnvVar, HTTPGetAction, LabelSelector, Pod, Probe, Volume}

class DeploymentPreparer(config: K8sDeploymentManagerConfig) {

  def prepare(processVersion: ProcessVersion, configMapId: String): Deployment = {
    val userConfigurationBasedDeployment = DeploymentUtils.parseDeploymentWithFallback(config.k8sDeploymentConfig)
    applyDeploymentDefaults(userConfigurationBasedDeployment, processVersion, configMapId)
  }

  private def applyDeploymentDefaults(userConfigurationBasedDeployment: Deployment, processVersion: ProcessVersion, configMapId: String) = {
    val objectName = objectNameForScenario(processVersion, None)
    val annotations = Map(scenarioVersionAnnotation -> processVersion.asJson.spaces2)
    val labels = labelsForScenario(processVersion)

    //we use 'OptionOptics some' here and do not worry about withDefault because _.spec is provided in defaultMinimalDeployment
    val deploymentSpecLens = GenLens[Deployment](_.spec) composePrism some
    val templateSpecLens = deploymentSpecLens composeLens GenLens[Deployment.Spec](_.template.spec) composeIso withDefault(Pod.Spec())
    val deploymentLens =
      GenLens[Deployment](_.metadata.name).set(objectName) andThen
        GenLens[Deployment](_.metadata.labels).modify(_ ++ labels) andThen
        GenLens[Deployment](_.metadata.annotations).modify(_ ++ annotations) andThen
        //here we use id to avoid sanitization problems
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.selector)).set(LabelSelector(IsEqualRequirement(scenarioIdLabel, processVersion.processId.value.toString))) andThen
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.strategy)).modify(maybeStrategy => maybeStrategy.orElse(Some(Deployment.Strategy.Recreate))) andThen
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.replicas)).modify(maybeReplicas => maybeReplicas.orElse(Some(2))) andThen
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.template.metadata.name)).set(objectName) andThen
        (deploymentSpecLens composeLens GenLens[Deployment.Spec](_.template.metadata.labels)).modify(_ ++ labels) andThen
        (templateSpecLens composeLens GenLens[Pod.Spec](_.volumes)).modify(_ ++ List(Volume("configmap", Volume.ConfigMapVolumeSource(configMapId)))) andThen
        (templateSpecLens composeLens GenLens[Pod.Spec](_.containers)).modify(containers => modifyContainers(containers))
    deploymentLens(userConfigurationBasedDeployment)
  }

  private def withDefault[A](defaultValue: A): Iso[Option[A], A] =
    Iso[Option[A], A](_.getOrElse(defaultValue))(value => if (value == defaultValue) None else Some(value))

  private def modifyContainers(containers: List[Container]): List[Container] = {
    val image = s"${config.dockerImageName}:${config.dockerImageTag}"
    val runtimeContainer = Container(
      name = "runtime",
      image = image,
      env = List(
        EnvVar("SCENARIO_FILE", "/data/scenario.json"),
        EnvVar("CONFIG_FILE", "/opt/nussknacker/conf/application.conf,/data/modelConfig.conf"),
        // We pass POD_NAME, because there is no option to pass only replica hash which is appended to pod name.
        // Hash will be extracted on entrypoint side.
        EnvVar("POD_NAME", FieldRef("metadata.name"))
      ),
      volumeMounts = List(
        Volume.Mount(name = "configmap", mountPath = "/data")
      ),
      // used standard AkkaManagement see HealthCheckServerRunner for details
      readinessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/ready"))),
      livenessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/alive")))
    )

    def modifyRuntimeContainer(value: Container): Container = {
      val containerLens = GenLens[Container](_.env).modify(_ ++ runtimeContainer.env) andThen
        GenLens[Container](_.volumeMounts).modify(_ ++ runtimeContainer.volumeMounts) andThen
        GenLens[Container](_.readinessProbe).modify(_.orElse(runtimeContainer.readinessProbe)) andThen
        GenLens[Container](_.livenessProbe).modify(_.orElse(runtimeContainer.livenessProbe))
      containerLens(value)
    }

    val (runtimeContainers, nonRuntimeContainers) = containers.partition(_.name == "runtime")

    (runtimeContainers match {
      case Nil => List(runtimeContainer)
      case single :: Nil => List(modifyRuntimeContainer(single))
      case _ => throw new IllegalStateException("Deployment should have only one 'runtime' container")
    }) ++ nonRuntimeContainers
  }
}

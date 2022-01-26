package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.circe.syntax.EncoderOps
import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.k8s.manager.{K8sDeploymentManager, K8sDeploymentManagerConfig}
import skuber.EnvVar.FieldRef
import skuber.apps.v1.Deployment
import skuber.{Container, EnvVar, HTTPGetAction, LabelSelector, ObjectMeta, Pod, Probe, Volume}

import scala.jdk.CollectionConverters.seqAsJavaListConverter

class DeploymentPreparerTest extends FunSuite {

  val configMapId = "fooConfigMap"
  val processVersion: ProcessVersion = ProcessVersion.empty

  private val labels = Map(
    "nussknacker.io/scenarioName" -> "-e3b0c44298",
    "nussknacker.io/scenarioId" -> "1",
    "nussknacker.io/scenarioVersion" -> "1",
  )
  private val anotations = Map("nussknacker.io/scenarioVersion" -> processVersion.asJson.spaces2)

  test("should prepare deployment when k8sDeploymentConfig is empty") {
    val deploymentPreparer = new DeploymentPreparer(K8sDeploymentManagerConfig())
    val preparedDeployment = deploymentPreparer.prepare(processVersion, configMapId, 2)

    preparedDeployment shouldBe Deployment(
      metadata = ObjectMeta(
        name = "scenario-1-x",
        labels = labels,
        annotations = anotations
      ),
      spec = Some(Deployment.Spec(
        replicas = Some(2),
        strategy = Some(Deployment.Strategy.Recreate),
        //here we use id to avoid sanitization problems
        selector = LabelSelector(LabelSelector.IsEqualRequirement(K8sDeploymentManager.scenarioIdLabel, "1")),
        progressDeadlineSeconds = None,
        minReadySeconds = 10,
        template = Pod.Template.Spec(
          metadata = ObjectMeta(
            name = "scenario-1-x",
            labels = labels
          ), spec = Some(
            Pod.Spec(containers = List(
              Container(
                name = "runtime",
                image = s"touk/nussknacker-lite-kafka-runtime:${BuildInfo.version}",
                env = List(
                  EnvVar("SCENARIO_FILE", "/data/scenario.json"),
                  EnvVar("CONFIG_FILE", "/opt/nussknacker/conf/application.conf,/data/modelConfig.conf"),
                  EnvVar("DEPLOYMENT_CONFIG_FILE", "/data/deploymentConfig.conf"),
                  // We pass POD_NAME, because there is no option to pass only replica hash which is appended to pod name.
                  // Hash will be extracted on entrypoint side.
                  EnvVar("POD_NAME", FieldRef("metadata.name"))
                ),
                volumeMounts = List(
                  Volume.Mount(name = "configmap", mountPath = "/data")
                ),
                // used standard AkkaManagement see HealthCheckServerRunner for details
                readinessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/ready"), periodSeconds = Some(1), failureThreshold = Some(60))),
                livenessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/alive")))
              )),
              volumes = List(
                Volume("configmap", Volume.ConfigMapVolumeSource(configMapId))
              )
            ))
        )
      )))
  }

  test("should prepare deployment when k8sDeploymentConfig is provided") {
    val config = K8sDeploymentManagerConfig(k8sDeploymentConfig =
      ConfigFactory.empty()
        .withValue("metadata.name", ConfigValueFactory.fromAnyRef("shouldBeOverriden"))
        .withValue("metadata.labels.my-label", ConfigValueFactory.fromAnyRef("my-key"))
        .withValue("metadata.annotations.my-label", ConfigValueFactory.fromAnyRef("my-key"))
        .withValue("spec.replicas", ConfigValueFactory.fromAnyRef(3))
        .withValue("spec.minReadySeconds", ConfigValueFactory.fromAnyRef(3))
        .withValue("spec.progressDeadlineSeconds", ConfigValueFactory.fromAnyRef(3))
        .withValue("spec.strategy", ConfigValueFactory.fromAnyRef("RollingUpdate"))
        .withValue("spec.selector.matchLabels.override", ConfigValueFactory.fromAnyRef("shouldBeOverriden"))
        .withValue("spec.template.metadata.name", ConfigValueFactory.fromAnyRef("shouldBeOverriden"))
        .withValue("spec.template.metadata.labels.my-label", ConfigValueFactory.fromAnyRef("my-key"))
        .withValue("spec.template.spec.volumes", ConfigValueFactory.fromIterable(List(
          ConfigFactory.empty()
            .withValue("name", ConfigValueFactory.fromAnyRef("my-volume")).root()
        ).asJava))
        .withValue("spec.template.spec.containers", ConfigValueFactory.fromIterable(List(
          ConfigFactory.empty()
            .withValue("name", ConfigValueFactory.fromAnyRef("my-container"))
            .withValue("image", ConfigValueFactory.fromAnyRef("my-image"))
            .root()
        ).asJava))
    )

    val deploymentPreparer = new DeploymentPreparer(config)
    val preparedDeployment = deploymentPreparer.prepare(ProcessVersion.empty, configMapId, 2)

    preparedDeployment shouldBe Deployment(
      metadata = ObjectMeta(
        name = "scenario-1-x",
        labels = Map("my-label" -> "my-key") ++ labels,
        annotations = Map("my-label" -> "my-key") ++ anotations
      ),
      spec = Some(Deployment.Spec(
        replicas = Some(3),
        strategy = Some(Deployment.Strategy.apply(Deployment.StrategyType.RollingUpdate, None)),
        //here we use id to avoid sanitization problems
        selector = LabelSelector(LabelSelector.IsEqualRequirement(K8sDeploymentManager.scenarioIdLabel, "1")),
        progressDeadlineSeconds = Some(3),
        minReadySeconds = 3,
        template = Pod.Template.Spec(
          metadata = ObjectMeta(
            name = "scenario-1-x",
            labels = Map("my-label" -> "my-key") ++ labels
          ), spec = Some(
            Pod.Spec(containers = List(
              Container(
                name = "runtime",
                image = s"touk/nussknacker-lite-kafka-runtime:${BuildInfo.version}",
                env = List(
                  EnvVar("SCENARIO_FILE", "/data/scenario.json"),
                  EnvVar("CONFIG_FILE", "/opt/nussknacker/conf/application.conf,/data/modelConfig.conf"),
                  EnvVar("DEPLOYMENT_CONFIG_FILE", "/data/deploymentConfig.conf"),
                  // We pass POD_NAME, because there is no option to pass only replica hash which is appended to pod name.
                  // Hash will be extracted on entrypoint side.
                  EnvVar("POD_NAME", FieldRef("metadata.name"))
                ),
                volumeMounts = List(
                  Volume.Mount(name = "configmap", mountPath = "/data")
                ),
                // used standard AkkaManagement see HealthCheckServerRunner for details
                readinessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/ready"), periodSeconds = Some(1), failureThreshold = Some(60))),
                livenessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/alive")))
              ),
              Container(
                name = "my-container",
                image = "my-image"
              )
            ),
              volumes = List(
                Volume("my-volume", Volume.GenericVolumeSource("{\"name\":\"my-volume\"}")),
                Volume("configmap", Volume.ConfigMapVolumeSource(configMapId)),
              )
            ))
        )
      )))
  }

  test("should extend runtime container") {
    val config = K8sDeploymentManagerConfig(k8sDeploymentConfig =
      ConfigFactory.empty()
        .withValue("spec.template.spec.containers", ConfigValueFactory.fromIterable(List(
          ConfigFactory.empty()
            .withValue("name", ConfigValueFactory.fromAnyRef("runtime"))
            .withValue("image", ConfigValueFactory.fromAnyRef("my-image"))
            .root()
        ).asJava))
    )
    val deploymentPreparer = new DeploymentPreparer(config)
    val preparedDeployment = deploymentPreparer.prepare(ProcessVersion.empty, configMapId, 2)

    preparedDeployment shouldBe Deployment(
      metadata = ObjectMeta(
        name = "scenario-1-x",
        labels = labels,
        annotations = anotations
      ),
      spec = Some(Deployment.Spec(
        replicas = Some(2),
        strategy = Some(Deployment.Strategy.Recreate),
        //here we use id to avoid sanitization problems
        selector = LabelSelector(LabelSelector.IsEqualRequirement(K8sDeploymentManager.scenarioIdLabel, "1")),
        progressDeadlineSeconds = None,
        minReadySeconds = 10,
        template = Pod.Template.Spec(
          metadata = ObjectMeta(
            name = "scenario-1-x",
            labels = labels
          ), spec = Some(
            Pod.Spec(containers = List(
              Container(
                name = "runtime",
                image = s"my-image",
                env = List(
                  EnvVar("SCENARIO_FILE", "/data/scenario.json"),
                  EnvVar("CONFIG_FILE", "/opt/nussknacker/conf/application.conf,/data/modelConfig.conf"),
                  EnvVar("DEPLOYMENT_CONFIG_FILE", "/data/deploymentConfig.conf"),
                  // We pass POD_NAME, because there is no option to pass only replica hash which is appended to pod name.
                  // Hash will be extracted on entrypoint side.
                  EnvVar("POD_NAME", FieldRef("metadata.name"))
                ),
                volumeMounts = List(
                  Volume.Mount(name = "configmap", mountPath = "/data")
                ),
                // used standard AkkaManagement see HealthCheckServerRunner for details
                readinessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/ready"), periodSeconds = Some(1), failureThreshold = Some(60))),
                livenessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/alive")))
              )),
              volumes = List(
                Volume("configmap", Volume.ConfigMapVolumeSource(configMapId))
              )
            ))
        )
      )))
  }

  test("should throw when configured more than one 'runtime' container") {
    val config = K8sDeploymentManagerConfig(k8sDeploymentConfig =
      ConfigFactory.empty()
        .withValue("spec.template.spec.containers", ConfigValueFactory.fromIterable(
          List(ConfigFactory.empty()
            .withValue("name", ConfigValueFactory.fromAnyRef("runtime"))
            .withValue("image", ConfigValueFactory.fromAnyRef("my-image"))
            .root(),
            ConfigFactory.empty()
              .withValue("name", ConfigValueFactory.fromAnyRef("runtime"))
              .withValue("image", ConfigValueFactory.fromAnyRef("another-image"))
              .root(),
          ).asJava))
    )

    val deploymentPreparer = new DeploymentPreparer(config)
    assertThrows[IllegalStateException] {
      deploymentPreparer.prepare(ProcessVersion.empty, configMapId, 2)
    }
  }
}

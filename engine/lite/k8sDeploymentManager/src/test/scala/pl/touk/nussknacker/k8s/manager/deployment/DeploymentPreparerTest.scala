package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.circe.syntax.EncoderOps
import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.k8s.manager.{K8sDeploymentManager, K8sDeploymentManagerConfig}
import skuber.EnvVar.{FieldRef, SecretKeyRef}
import skuber.Resource.Quantity
import skuber.apps.v1.Deployment
import skuber.{Container, EnvVar, HTTPGetAction, LabelSelector, ObjectMeta, Pod, Probe, Volume}

import scala.collection.JavaConverters._
import scala.jdk.CollectionConverters.seqAsJavaListConverter

class DeploymentPreparerTest extends FunSuite {

  val configMapId = "fooConfigMap"
  val loggingConfigMapId = "barConfigMap"
  val secretId = "fooSecret"
  val resources = MountableResources(configMapId, loggingConfigMapId, secretId)

  val processVersion: ProcessVersion = ProcessVersion.empty

  val nussknackerInstanceName = "foo-release"

  private val labels = Map(
    "nussknacker.io/scenarioName" -> "-e3b0c44298",
    "nussknacker.io/scenarioId" -> "1",
    "nussknacker.io/scenarioVersion" -> "1",
  )
  private val anotations = Map("nussknacker.io/scenarioVersion" -> processVersion.asJson.spaces2)

  test("should prepare deployment when k8sDeploymentConfig is empty") {
    val deploymentPreparer = new DeploymentPreparer(K8sDeploymentManagerConfig())
    val preparedDeployment = deploymentPreparer.prepare(processVersion, resources, 2)

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
                  EnvVar("SCENARIO_FILE", "/config/scenario.json"),
                  EnvVar("CONFIG_FILE", "/opt/nussknacker/conf/application.conf,/model-config/modelConfig.conf"),
                  EnvVar("DEPLOYMENT_CONFIG_FILE", "/config/deploymentConfig.conf"),
                  EnvVar("LOGBACK_FILE", "/logging-config/logback.xml"),
                  // We pass POD_NAME, because there is no option to pass only replica hash which is appended to pod name.
                  // Hash will be extracted on entrypoint side.
                  EnvVar("POD_NAME", FieldRef("metadata.name"))
                ),
                volumeMounts = List(
                  Volume.Mount(name = "common-conf", mountPath = "/config"),
                  Volume.Mount(name = "logging-conf", mountPath = "/logging-config"),
                  Volume.Mount(name = "model-conf", mountPath = "/model-config")
                ),
                // used standard AkkaManagement see HealthCheckServerRunner for details
                readinessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/ready"), periodSeconds = Some(1), failureThreshold = Some(60))),
                livenessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/alive")))
              )),
              volumes = List(
                Volume("common-conf", Volume.ConfigMapVolumeSource(configMapId)),
                Volume("logging-conf", Volume.ConfigMapVolumeSource(loggingConfigMapId)),
                Volume("model-conf", Volume.Secret(secretId))
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
            .root(),
          ConfigFactory.empty()
            .withValue("name", ConfigValueFactory.fromAnyRef("runtime"))
            .withValue("resources", ConfigValueFactory.fromMap(
              Map(
                "requests" -> ConfigValueFactory.fromMap(Map("memory"-> "256Mi", "cpu"-> "20m").asJava),
                "limits" -> ConfigValueFactory.fromMap(Map("memory"-> "256Mi", "cpu"-> "20m").asJava)
              ).asJava
            ))
            .root()

        ).asJava)),
      nussknackerInstanceName = Some(nussknackerInstanceName)
    )

    val deploymentPreparer = new DeploymentPreparer(config)
    val preparedDeployment = deploymentPreparer.prepare(ProcessVersion.empty, resources, 2)

    preparedDeployment shouldBe Deployment(
      metadata = ObjectMeta(
        name = "foo-release-scenario-1-x",
        labels = Map("my-label" -> "my-key", "nussknacker.io/nussknackerInstanceName" -> nussknackerInstanceName) ++ labels,
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
            name = "foo-release-scenario-1-x",
            labels = Map("my-label" -> "my-key", "nussknacker.io/nussknackerInstanceName" -> nussknackerInstanceName) ++ labels
          ), spec = Some(
            Pod.Spec(containers = List(
              Container(
                name = "runtime",
                image = s"touk/nussknacker-lite-kafka-runtime:${BuildInfo.version}",
                env = List(
                  EnvVar("SCENARIO_FILE", "/config/scenario.json"),
                  EnvVar("CONFIG_FILE", "/opt/nussknacker/conf/application.conf,/model-config/modelConfig.conf"),
                  EnvVar("DEPLOYMENT_CONFIG_FILE", "/config/deploymentConfig.conf"),
                  EnvVar("LOGBACK_FILE", "/logging-config/logback.xml"),
                  // We pass POD_NAME, because there is no option to pass only replica hash which is appended to pod name.
                  // Hash will be extracted on entrypoint side.
                  EnvVar("POD_NAME", FieldRef("metadata.name"))
                ),
                volumeMounts = List(
                  Volume.Mount(name = "common-conf", mountPath = "/config"),
                  Volume.Mount(name = "logging-conf", mountPath = "/logging-config"),
                  Volume.Mount(name = "model-conf", mountPath = "/model-config")
                ),
                // used standard AkkaManagement see HealthCheckServerRunner for details
                readinessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/ready"), periodSeconds = Some(1), failureThreshold = Some(60))),
                livenessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/alive"))),
                resources = Some(
                  skuber.Resource.Requirements(
                    limits = Map("cpu"-> Quantity("20m"), "memory" -> Quantity("256Mi")),
                    requests = Map("cpu"-> Quantity("20m"), "memory" -> Quantity("256Mi"))
                  )
                )
              ),
              Container(
                name = "my-container",
                image = "my-image"
              )
            ),
              volumes = List(
                Volume("my-volume", Volume.GenericVolumeSource("{\"name\":\"my-volume\"}")),
                Volume("common-conf", Volume.ConfigMapVolumeSource(configMapId)),
                Volume("logging-conf", Volume.ConfigMapVolumeSource(loggingConfigMapId)),
                Volume("model-conf", Volume.Secret(secretId))
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
            .withValue("image", ConfigValueFactory.fromAnyRef("shouldBeOverriden"))
            .withValue("env", ConfigValueFactory.fromIterable(List(
              ConfigFactory.empty()
                .withValue("name", ConfigValueFactory.fromAnyRef("my-env-name"))
                .withValue("valueFrom.secretKeyRef.name", ConfigValueFactory.fromAnyRef("my-secret"))
                .withValue("valueFrom.secretKeyRef.key", ConfigValueFactory.fromAnyRef("my-key"))
                .root()
            ).asJava))
            .root()
        ).asJava))
    )
    val deploymentPreparer = new DeploymentPreparer(config)
    val preparedDeployment = deploymentPreparer.prepare(ProcessVersion.empty, resources, 2)

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
                  EnvVar("my-env-name", SecretKeyRef("my-key", "my-secret")),
                  EnvVar("SCENARIO_FILE", "/config/scenario.json"),
                  EnvVar("CONFIG_FILE", "/opt/nussknacker/conf/application.conf,/model-config/modelConfig.conf"),
                  EnvVar("DEPLOYMENT_CONFIG_FILE", "/config/deploymentConfig.conf"),
                  EnvVar("LOGBACK_FILE", "/logging-config/logback.xml"),
                  // We pass POD_NAME, because there is no option to pass only replica hash which is appended to pod name.
                  // Hash will be extracted on entrypoint side.
                  EnvVar("POD_NAME", FieldRef("metadata.name"))
                ),
                volumeMounts = List(
                  Volume.Mount(name = "common-conf", mountPath = "/config"),
                  Volume.Mount(name = "logging-conf", mountPath = "/logging-config"),
                  Volume.Mount(name = "model-conf", mountPath = "/model-config")
                ),
                // used standard AkkaManagement see HealthCheckServerRunner for details
                readinessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/ready"), periodSeconds = Some(1), failureThreshold = Some(60))),
                livenessProbe = Some(Probe(new HTTPGetAction(Left(8558), path = "/alive")))
              )),
              volumes = List(
                Volume("common-conf", Volume.ConfigMapVolumeSource(configMapId)),
                Volume("logging-conf", Volume.ConfigMapVolumeSource(loggingConfigMapId)),
                Volume("model-conf", Volume.Secret(secretId))
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
      deploymentPreparer.prepare(ProcessVersion.empty, resources, 2)
    }
  }
}

package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.FunSuite
import org.scalatest.Matchers.{contain, convertToAnyShouldWrapper, startWith}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.k8s.manager.{K8sDeploymentManager, K8sDeploymentManagerConfig}
import skuber.EnvVar.{FieldRef, StringValue}
import skuber.apps.v1.Deployment
import skuber.apps.v1.Deployment.StrategyType
import skuber.{EnvVar, LabelSelector, Volume}

class DeploymentPreparerTest extends FunSuite {

  val configMapId = "fooConfigMap"

  test("should prepare deployment when k8sDeploymentConfig is empty") {

    val deployment = DeploymentPreparer(ProcessVersion.empty, K8sDeploymentManagerConfig(), configMapId)

    deployment.metadata.name shouldBe "scenario-1-x"
    deployment.metadata.labels.keys should contain(K8sDeploymentManager.scenarioNameLabel)
    deployment.metadata.labels.keys should contain(K8sDeploymentManager.scenarioIdLabel)
    deployment.metadata.labels.keys should contain(K8sDeploymentManager.scenarioVersionLabel)
    deployment.metadata.annotations.keys should contain(K8sDeploymentManager.scenarioVersionAnnotation)

    deployment.spec.get.replicas shouldBe Some(2)
    deployment.spec.get.minReadySeconds shouldBe 10
    deployment.spec.get.progressDeadlineSeconds shouldBe None
    deployment.spec.get.strategy shouldBe Some(Deployment.Strategy.Recreate)
    deployment.spec.get.selector shouldBe LabelSelector(LabelSelector.IsEqualRequirement(K8sDeploymentManager.scenarioIdLabel, "1"))

    deployment.spec.get.template.metadata.name shouldBe "scenario-1-x"
    deployment.spec.get.template.metadata.labels.keys should contain(K8sDeploymentManager.scenarioNameLabel)
    deployment.spec.get.template.metadata.labels.keys should contain(K8sDeploymentManager.scenarioIdLabel)
    deployment.spec.get.template.metadata.labels.keys should contain(K8sDeploymentManager.scenarioVersionLabel)

    deployment.spec.get.template.spec.get.volumes should contain(Volume("configmap", Volume.ConfigMapVolumeSource("fooConfigMap")))
    deployment.spec.get.template.spec.get.containers.head.name shouldBe "runtime"
    deployment.spec.get.template.spec.get.containers.head.image should startWith("touk/nussknacker-lite-kafka-runtime")
    deployment.spec.get.template.spec.get.containers.head.env should contain(EnvVar("SCENARIO_FILE", StringValue("/data/scenario.json")))
    deployment.spec.get.template.spec.get.containers.head.env should contain(EnvVar("CONFIG_FILE", StringValue("/opt/nussknacker/conf/application.conf,/data/modelConfig.conf")))
    deployment.spec.get.template.spec.get.containers.head.env should contain(EnvVar("POD_NAME", FieldRef("metadata.name")))
    deployment.spec.get.template.spec.get.containers.head.volumeMounts.head.name shouldBe "configmap"
    deployment.spec.get.template.spec.get.containers.head.volumeMounts.head.mountPath shouldBe "/data"

  }


  test("should prepare deployment when k8sDeploymentConfig is provided") {
    import scala.jdk.CollectionConverters.seqAsJavaListConverter
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
    val deployment = DeploymentPreparer(ProcessVersion.empty, config, configMapId)

    deployment.metadata.name shouldBe "scenario-1-x"
    deployment.metadata.labels.keys should contain(K8sDeploymentManager.scenarioNameLabel)
    deployment.metadata.labels.keys should contain(K8sDeploymentManager.scenarioIdLabel)
    deployment.metadata.labels.keys should contain(K8sDeploymentManager.scenarioVersionLabel)
    deployment.metadata.labels.keys should contain("my-label")
    deployment.metadata.annotations.keys should contain(K8sDeploymentManager.scenarioVersionAnnotation)
    deployment.metadata.annotations.keys should contain("my-label")

    deployment.spec.get.replicas shouldBe Some(3)
    deployment.spec.get.minReadySeconds shouldBe 3
    deployment.spec.get.progressDeadlineSeconds shouldBe Some(3)
    deployment.spec.get.strategy.get._type shouldBe StrategyType.RollingUpdate
    deployment.spec.get.selector shouldBe LabelSelector(LabelSelector.IsEqualRequirement(K8sDeploymentManager.scenarioIdLabel, "1"))

    deployment.spec.get.template.metadata.name shouldBe "scenario-1-x"
    deployment.spec.get.template.metadata.labels.keys should contain(K8sDeploymentManager.scenarioNameLabel)
    deployment.spec.get.template.metadata.labels.keys should contain(K8sDeploymentManager.scenarioIdLabel)
    deployment.spec.get.template.metadata.labels.keys should contain(K8sDeploymentManager.scenarioVersionLabel)
    deployment.spec.get.template.metadata.labels.keys should contain("my-label")

    deployment.spec.get.template.spec.get.volumes should contain(Volume("configmap", Volume.ConfigMapVolumeSource("fooConfigMap")))
    deployment.spec.get.template.spec.get.volumes should contain(Volume("my-volume", Volume.GenericVolumeSource("{\"name\":\"my-volume\"}")))
    deployment.spec.get.template.spec.get.containers.size shouldBe 2
    deployment.spec.get.template.spec.get.containers.head.name shouldBe "runtime"
    deployment.spec.get.template.spec.get.containers.head.image should startWith("touk/nussknacker-lite-kafka-runtime")
    deployment.spec.get.template.spec.get.containers.head.env should contain(EnvVar("SCENARIO_FILE", StringValue("/data/scenario.json")))
    deployment.spec.get.template.spec.get.containers.head.env should contain(EnvVar("CONFIG_FILE", StringValue("/opt/nussknacker/conf/application.conf,/data/modelConfig.conf")))
    deployment.spec.get.template.spec.get.containers.head.env should contain(EnvVar("POD_NAME", FieldRef("metadata.name")))
    deployment.spec.get.template.spec.get.containers.head.volumeMounts.head.name shouldBe "configmap"
    deployment.spec.get.template.spec.get.containers.head.volumeMounts.head.mountPath shouldBe "/data"
  }

  test("should extend runtime container") {
    import scala.jdk.CollectionConverters.seqAsJavaListConverter
    val config = K8sDeploymentManagerConfig(k8sDeploymentConfig =
      ConfigFactory.empty()
        .withValue("spec.template.spec.containers", ConfigValueFactory.fromIterable(List(
          ConfigFactory.empty()
            .withValue("name", ConfigValueFactory.fromAnyRef("runtime"))
            .withValue("image", ConfigValueFactory.fromAnyRef("my-image"))
            .root()
        ).asJava))
    )
    val deployment = DeploymentPreparer(ProcessVersion.empty, config, configMapId)

    deployment.spec.get.template.spec.get.containers.size shouldBe 1
    deployment.spec.get.template.spec.get.containers.head.name shouldBe "runtime"
    deployment.spec.get.template.spec.get.containers.head.image should startWith("my-image")
    deployment.spec.get.template.spec.get.containers.head.env should contain(EnvVar("SCENARIO_FILE", StringValue("/data/scenario.json")))
    deployment.spec.get.template.spec.get.containers.head.env should contain(EnvVar("CONFIG_FILE", StringValue("/opt/nussknacker/conf/application.conf,/data/modelConfig.conf")))
    deployment.spec.get.template.spec.get.containers.head.env should contain(EnvVar("POD_NAME", FieldRef("metadata.name")))
    deployment.spec.get.template.spec.get.containers.head.volumeMounts.head.name shouldBe "configmap"
    deployment.spec.get.template.spec.get.containers.head.volumeMounts.head.mountPath shouldBe "/data"
  }
}

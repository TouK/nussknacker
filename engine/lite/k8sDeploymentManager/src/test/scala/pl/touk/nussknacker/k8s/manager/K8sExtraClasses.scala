package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.k8s.manager.K8sExtraClasses.{extraClassesSecretName, serviceLoaderConfigItemName}
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import play.api.libs.json.Format
import skuber.{ObjectMeta, ResourceDefinition, Secret}
import skuber.api.client.{KubernetesClient, LoggingContext}
import skuber.json.format._

import java.net.URL
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

class K8sExtraClasses(k8s: KubernetesClient, classes: List[Class[_]], serviceLoaderConfigURL: URL)
    extends VeryPatientScalaFutures {

  private val k8sUtils = new K8sUtils(k8s)

  def withExtraClassesSecret(action: => Unit): Unit = {
    createExtraClassesSecret()
    try {
      action
    } finally {
      cleanup()
    }
  }

  // We hold extra classes as a secret, because ConfigMap in scuber doesn't accept binary data
  private def createExtraClassesSecret(): Secret = {
    val classesData =
      classes.map(cl => cl.getSimpleName -> IOUtils.toByteArray(cl.getResource(cl.getSimpleName + ".class"))).toMap
    val serviceLoaderConfigData = Map(serviceLoaderConfigItemName -> IOUtils.toByteArray(serviceLoaderConfigURL))
    cleanup()
    k8s
      .create(
        Secret(
          metadata = ObjectMeta(
            name = extraClassesSecretName
          ),
          data = classesData ++ serviceLoaderConfigData
        )
      )
      .futureValue
  }

  def secretReferenceResourcePart: java.util.Map[String, _] = {
    Map(
      "secretName" -> extraClassesSecretName,
      "items" ->
        itemRelativePaths.toList.map { case (key, path) =>
          Map(
            "key"  -> fromAnyRef(key),
            "path" -> fromAnyRef(path)
          ).asJava
        }.asJava
    ).asJava
  }

  private def itemRelativePaths: Map[String, String] = {
    classes.map(cl => cl.getSimpleName -> (cl.getName.replaceAll("\\.", "/") + ".class")).toMap +
      (serviceLoaderConfigItemName -> serviceLoaderConfigURL.getPath.replaceAll(".*/(META-INF/services/.*)", "$1"))
  }

  private def cleanup(): Unit = {
    import ExecutionContext.Implicits.global
    k8sUtils.deleteIfExists[Secret](extraClassesSecretName).futureValue
  }

}

object K8sExtraClasses {

  private val extraClassesSecretName = "extra-classes"

  private val serviceLoaderConfigItemName = "serviceLoaderConfig"

  def serviceLoaderConfigURL(resourceHolder: Class[_], resourceType: Class[_]): URL = {
    resourceHolder.getResource("/META-INF/services/" + resourceType.getName)
  }

}

package pl.touk.nussknacker.k8s.manager

import org.apache.commons.codec.digest.DigestUtils
import play.api.libs.json.Format
import skuber.api.client.{KubernetesClient, LoggingContext}
import skuber.{ObjectResource, ResourceDefinition}

import scala.concurrent.{ExecutionContext, Future}

class K8sUtils(client: KubernetesClient) {

  //TODO: use https://kubernetes.io/docs/reference/using-api/server-side-apply/ in the future
  private[manager] def createOrUpdate[O<:ObjectResource](data: O)
                                               (implicit fmt: Format[O], rd: ResourceDefinition[O],
                                                lc: LoggingContext, ec: ExecutionContext): Future[O] = {
    client.getOption[O](data.name).flatMap {
      case Some(_) => client.update(data)
      case None => client.create(data)
    }
  }

}

object K8sUtils {

  //Object names cannot have underscores in name...
  def sanitizeObjectName(original: String, append: String = ""): String = {
    sanitizeName(original, canHaveUnderscore = false, append = append)
  }

  //Value label: https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#syntax-and-character-set
  def sanitizeLabel(original: String, append: String = ""): String = {
    sanitizeName(original, canHaveUnderscore = true, append = append)
  }

  //TODO: generate better correct name for 'strange' scenario names?
  private[manager] def sanitizeName(base: String, canHaveUnderscore: Boolean, append: String = ""): String = {
    val underscores = if (canHaveUnderscore) "_" else ""
    base.toLowerCase
      .replaceAll(s"[^a-zA-Z0-9${underscores}\\-.]+", "-")
      //need to have alphanumeric at beginning and end...
      .replaceAll("^([^a-zA-Z0-9])", "x$1")
      .replaceAll("([^a-zA-Z0-9])$", "$1x")
      .take(63 - append.length) + append
  }

  //https://github.com/kubernetes/kubectl/blob/master/pkg/util/hash/hash.go#L105 - we don't care about bad words...
  private[manager] def shortHash(data: String): String = DigestUtils.sha256Hex(data).take(10)


}

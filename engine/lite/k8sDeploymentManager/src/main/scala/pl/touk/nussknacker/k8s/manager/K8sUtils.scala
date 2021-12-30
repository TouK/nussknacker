package pl.touk.nussknacker.k8s.manager

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

}

package pl.touk.nussknacker.ui.security.api

trait AnonymousAccessSupport {
  def getAnonymousRole: Option[String]
}

trait NoAnonymousAccessSupport extends AnonymousAccessSupport {
  override def getAnonymousRole: Option[String] = None
}

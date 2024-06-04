package pl.touk.nussknacker.ui.security.api

trait ImpersonationSupport {
  def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData]
}

trait NoImpersonationSupport extends ImpersonationSupport {
  override def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData] = None
}

final case class ImpersonatedUserData(id: String, username: String, roles: Set[String])

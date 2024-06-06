package pl.touk.nussknacker.ui.security.api

trait ImpersonationSupport {
  def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData]

  def getImpersonatedUserDataWithSupportCheck(
      impersonatedUserIdentity: String
  ): Either[ImpersonationNotSupported.type, Option[ImpersonatedUserData]] =
    Right(getImpersonatedUserData(impersonatedUserIdentity))

}

trait NoImpersonationSupport extends ImpersonationSupport {
  override def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData] = None

  override def getImpersonatedUserDataWithSupportCheck(
      impersonatedUserIdentity: String
  ): Either[ImpersonationNotSupported.type, Option[ImpersonatedUserData]] = Left(ImpersonationNotSupported)

}

final case class ImpersonatedUserData(id: String, username: String, roles: Set[String])

case object ImpersonationNotSupported

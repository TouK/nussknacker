package pl.touk.nussknacker.ui.security.api

sealed trait ImpersonationSupport {

  def getImpersonatedUserDataWithSupportCheck(
      impersonatedUserIdentity: String
  ): Either[ImpersonationNotSupported.type, Option[ImpersonatedUserData]]

}

abstract class ImpersonationSupported extends ImpersonationSupport {
  def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData]

  override final def getImpersonatedUserDataWithSupportCheck(
      impersonatedUserIdentity: String
  ): Either[ImpersonationNotSupported.type, Option[ImpersonatedUserData]] =
    Right(getImpersonatedUserData(impersonatedUserIdentity))

}

object NoImpersonationSupport extends ImpersonationSupport {

  override final def getImpersonatedUserDataWithSupportCheck(
      impersonatedUserIdentity: String
  ): Either[ImpersonationNotSupported.type, Option[ImpersonatedUserData]] = Left(ImpersonationNotSupported)

}

final case class ImpersonatedUserData(id: String, username: String, roles: Set[String])

case object ImpersonationNotSupported

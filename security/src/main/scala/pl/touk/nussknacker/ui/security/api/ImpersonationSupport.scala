package pl.touk.nussknacker.ui.security.api

sealed trait ImpersonationSupport {

  def getImpersonatedUserDataWithSupportCheck(
      impersonatedUserIdentity: String
  ): Either[ImpersonationNotSupported.type, Option[ImpersonatedUserData]]

  def toImpersonatedUserIdentityWithSupportCheck(
      userData: ImpersonatedUserData
  ): Either[ImpersonationNotSupported.type, String]

}

abstract class ImpersonationSupported extends ImpersonationSupport {
  def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData]
  def toImpersonatedUserIdentity(userData: ImpersonatedUserData): String

  override final def getImpersonatedUserDataWithSupportCheck(
      impersonatedUserIdentity: String
  ): Either[ImpersonationNotSupported.type, Option[ImpersonatedUserData]] =
    Right(getImpersonatedUserData(impersonatedUserIdentity))

  override def toImpersonatedUserIdentityWithSupportCheck(
      userData: ImpersonatedUserData
  ): Either[ImpersonationNotSupported.type, String] =
    Right(toImpersonatedUserIdentity(userData))

}

object NoImpersonationSupport extends ImpersonationSupport {

  override final def getImpersonatedUserDataWithSupportCheck(
      impersonatedUserIdentity: String
  ): Either[ImpersonationNotSupported.type, Option[ImpersonatedUserData]] = Left(ImpersonationNotSupported)

  override def toImpersonatedUserIdentityWithSupportCheck(
      userData: ImpersonatedUserData
  ): Either[ImpersonationNotSupported.type, String] = Left(ImpersonationNotSupported)

}

final case class ImpersonatedUserData(id: String, username: String, roles: Set[String])

case object ImpersonationNotSupported

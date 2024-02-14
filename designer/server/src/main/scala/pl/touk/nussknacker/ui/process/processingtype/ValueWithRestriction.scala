package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction.{
  AnyUser,
  UserWithAccessRightsToAnyOfCategories,
  ValueAccessRestriction
}
import pl.touk.nussknacker.ui.security.api.LoggedUser

final class ValueWithRestriction[+T](value: T, restriction: ValueAccessRestriction) {
  def map[TT](fun: T => TT): ValueWithRestriction[TT] = new ValueWithRestriction(value = fun(value), restriction)

  def valueWithAllowedAccess(accessPermission: Permission)(implicit user: LoggedUser): Option[T] = {
    restriction match {
      case AnyUser => Some(value)
      case UserWithAccessRightsToAnyOfCategories(categories) if categories.exists(user.can(_, accessPermission)) =>
        Some(value)
      case _ => None
    }
  }

}

object ValueWithRestriction {

  // This restriction is mainly to provide easier testing where we want to simulate simple setup without
  // `ProcessingTypeData` reload, without category access control.
  def anyUser[T](value: T): ValueWithRestriction[T] = new ValueWithRestriction(value, AnyUser)
  def userWithAccessRightsToAnyOfCategories[T](value: T, categories: Set[String]): ValueWithRestriction[T] =
    new ValueWithRestriction(value, UserWithAccessRightsToAnyOfCategories(categories))

  private sealed trait ValueAccessRestriction

  private case object AnyUser                                                             extends ValueAccessRestriction
  private final case class UserWithAccessRightsToAnyOfCategories(categories: Set[String]) extends ValueAccessRestriction

}

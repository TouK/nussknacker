package pl.touk.nussknacker.ui.process.processingtypedata

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.process.processingtypedata.ValueAccessPermission.{AnyUser, UserWithAccessRightsToCategory}
import pl.touk.nussknacker.ui.security.api.LoggedUser

/**
 *  NOTICE: This is probably *temporary* solution. We want to be able to:
 *  - reload elements of ProcessingTypeData
 *  - have option to add new ProcessingTypeData dynamically (though currently it's not supported)
 *  - use only those elements which are needed in given place
 *
 *  In the future we'll probably have better way of controlling how ProcessingTypeData is used, e.g. by
 *    - custom Directives or custom routes.
 *    - appropriate variant of Reader monad :)
 *
 *  Now we just want to be able to easily check usages of ProcessingTypeData elements
 *
 *  Currently, the only implementation is map-based, but in the future it will allow to reload ProcessingTypeData related stuff
 *  without restarting the app
 */
trait ProcessingTypeDataProvider[+T, +C] {

  def forType(processingType: ProcessingType)(implicit user: LoggedUser): Option[T]

  // TODO: replace with proper forType handling
  final def forTypeUnsafe(processingType: ProcessingType)(implicit user: LoggedUser): T = forType(processingType)
    .getOrElse(
      throw new IllegalArgumentException(
        s"Unknown ProcessingType: $processingType, known ProcessingTypes are: ${all.keys.mkString(", ")}"
      )
    )

  def all(implicit user: LoggedUser): Map[ProcessingType, T]

  // TODO: We should return a generic type that can produce views for users with access rights to certain categories only.
  //       Thanks to that we will be sure that no sensitive data leak
  def combined: C

  def mapValues[Y](fun: T => Y): ProcessingTypeDataProvider[Y, C] = {

    new ProcessingTypeDataProvider[Y, C] {

      override def forType(processingType: ProcessingType)(implicit user: LoggedUser): Option[Y] =
        ProcessingTypeDataProvider.this.forType(processingType).map(fun)

      override def all(implicit user: LoggedUser): Map[ProcessingType, Y] =
        ProcessingTypeDataProvider.this.all.mapValuesNow(fun)

      override def combined: C = ProcessingTypeDataProvider.this.combined
    }

  }

  def mapCombined[CC](fun: (=> C) => CC): ProcessingTypeDataProvider[T, CC] = {

    new ProcessingTypeDataProvider[T, CC] {

      override def forType(processingType: ProcessingType)(implicit user: LoggedUser): Option[T] =
        ProcessingTypeDataProvider.this.forType(processingType)

      override def all(implicit user: LoggedUser): Map[ProcessingType, T] = ProcessingTypeDataProvider.this.all

      override def combined: CC = fun(ProcessingTypeDataProvider.this.combined)
    }

  }

}

class MapBasedProcessingTypeDataProvider[T, C](map: Map[ProcessingType, ValueWithPermission[T]], getCombined: => C)
    extends ProcessingTypeDataProvider[T, C] {

  override def forType(processingType: ProcessingType)(implicit user: LoggedUser): Option[T] = allAuthorized
    .get(processingType)
    .map(_.getOrElse(throw new UnauthorizedError()))

  override def all(implicit user: LoggedUser): Map[ProcessingType, T] = allAuthorized.collect { case (k, Some(v)) =>
    (k, v)
  }

  private def allAuthorized(implicit user: LoggedUser): Map[ProcessingType, Option[T]] = map.map {
    case (k, ValueWithPermission(v, AnyUser)) => (k, Some(v))
    case (k, ValueWithPermission(v, UserWithAccessRightsToCategory(category))) if user.can(category, Permission.Read) =>
      (k, Some(v))
    case (k, _) => (k, None)
  }

  override lazy val combined: C = getCombined

}

object MapBasedProcessingTypeDataProvider {

  def withEmptyCombinedData[T](
      map: Map[ProcessingType, ValueWithPermission[T]]
  ): ProcessingTypeDataProvider[T, Nothing] = {
    new MapBasedProcessingTypeDataProvider[T, Nothing](
      map,
      throw new IllegalStateException("Processing type data provider does not have combined data!")
    )
  }

}

final case class ValueWithPermission[+T](value: T, permission: ValueAccessPermission) {
  def map[TT](fun: T => TT): ValueWithPermission[TT] = copy(value = fun(value))
}

object ValueWithPermission {
  def anyUser[T](value: T): ValueWithPermission[T] = ValueWithPermission(value, AnyUser)
}

sealed trait ValueAccessPermission

object ValueAccessPermission {
  case object AnyUser                                               extends ValueAccessPermission
  final case class UserWithAccessRightsToCategory(category: String) extends ValueAccessPermission
}

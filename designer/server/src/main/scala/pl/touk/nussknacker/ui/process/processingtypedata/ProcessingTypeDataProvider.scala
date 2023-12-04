package pl.touk.nussknacker.ui.process.processingtypedata

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider.{
  AnyUserPermission,
  UserWithCategoryReadPermission,
  ValueWithPermission
}
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

  def forType(typ: ProcessingType)(implicit user: LoggedUser): Option[T]

  // TODO: replace with proper forType handling
  final def forTypeUnsafe(typ: ProcessingType)(implicit user: LoggedUser): T = forType(typ)
    .getOrElse(throw new IllegalArgumentException(s"Unknown typ: $typ, known types are: ${all.keys.mkString(", ")}"))

  def all(implicit user: LoggedUser): Map[ProcessingType, T]

  // TODO: We should return type that can produce views for users with access to certain categories. Thanks to that
  //       we will be sure that no sensitive data leak
  def combined: C

  def mapValues[Y](fun: T => Y): ProcessingTypeDataProvider[Y, C] = {

    new ProcessingTypeDataProvider[Y, C] {

      override def forType(typ: ProcessingType)(implicit user: LoggedUser): Option[Y] =
        ProcessingTypeDataProvider.this.forType(typ).map(fun)

      override def all(implicit user: LoggedUser): Map[ProcessingType, Y] =
        ProcessingTypeDataProvider.this.all.mapValuesNow(fun)

      override def combined: C = ProcessingTypeDataProvider.this.combined
    }

  }

  def mapCombined[CC](fun: (=> C) => CC): ProcessingTypeDataProvider[T, CC] = {

    new ProcessingTypeDataProvider[T, CC] {

      override def forType(typ: ProcessingType)(implicit user: LoggedUser): Option[T] =
        ProcessingTypeDataProvider.this.forType(typ)

      override def all(implicit user: LoggedUser): Map[ProcessingType, T] = ProcessingTypeDataProvider.this.all

      override def combined: CC = fun(ProcessingTypeDataProvider.this.combined)
    }

  }

}

class MapBasedProcessingTypeDataProvider[T, C](map: Map[ProcessingType, ValueWithPermission[T]], getCombined: => C)
    extends ProcessingTypeDataProvider[T, C] {

  override def forType(typ: ProcessingType)(implicit user: LoggedUser): Option[T] = map.get(typ).collect {
    case ValueWithPermission(v, AnyUserPermission)                                                               => v
    case ValueWithPermission(v, UserWithCategoryReadPermission(category)) if user.can(category, Permission.Read) => v
  }

  override def all(implicit user: LoggedUser): Map[ProcessingType, T] = map.collect {
    case (k, ValueWithPermission(v, AnyUserPermission)) => (k, v)
    case (k, ValueWithPermission(v, UserWithCategoryReadPermission(category))) if user.can(category, Permission.Read) =>
      (k, v)
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

  case class ValueWithPermission[T](value: T, permission: ValueAccessPermission)

  sealed trait ValueAccessPermission
  case object AnyUserPermission                               extends ValueAccessPermission
  case class UserWithCategoryReadPermission(category: String) extends ValueAccessPermission

}

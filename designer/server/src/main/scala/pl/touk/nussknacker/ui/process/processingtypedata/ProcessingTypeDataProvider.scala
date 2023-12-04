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

  // TODO: replace with proper forType handling
  final def forTypeUnsafe(processingType: ProcessingType)(implicit user: LoggedUser): T = forType(processingType)
    .getOrElse(
      throw new IllegalArgumentException(
        s"Unknown ProcessingType: $processingType, known ProcessingTypes are: ${all.keys.mkString(", ")}"
      )
    )

  final def forType(processingType: ProcessingType)(implicit user: LoggedUser): Option[T] = allAuthorized
    .get(processingType)
    .map(_.getOrElse(throw new UnauthorizedError()))

  def all(implicit user: LoggedUser): Map[ProcessingType, T] = allAuthorized.collect { case (k, Some(v)) =>
    (k, v)
  }

  private def allAuthorized(implicit user: LoggedUser): Map[ProcessingType, Option[T]] = state.all.map {
    case (k, ValueWithPermission(v, AnyUser)) => (k, Some(v))
    case (k, ValueWithPermission(v, UserWithAccessRightsToCategory(category))) if user.can(category, Permission.Read) =>
      (k, Some(v))
    case (k, _) => (k, None)
  }

  // TODO: We should return a generic type that can produce views for users with access rights to certain categories only.
  //       Thanks to that we will be sure that no sensitive data leak
  def combined: C = state.getCombined()

  private[processingtypedata] def state: ProcessingTypeDataState[T, C]

  def mapValues[TT](fun: T => TT): ProcessingTypeDataProvider[TT, C] =
    new TransformingProcessingTypeDataProvider[T, C, TT, C](this, _.mapValues(fun))

  def mapCombined[CC](fun: C => CC): ProcessingTypeDataProvider[T, CC] =
    new TransformingProcessingTypeDataProvider[T, C, T, CC](this, _.mapCombined(fun))

}

class TransformingProcessingTypeDataProvider[T, C, TT, CC](
    observed: ProcessingTypeDataProvider[T, C],
    transformState: ProcessingTypeDataState[T, C] => ProcessingTypeDataState[TT, CC]
) extends ProcessingTypeDataProvider[TT, CC] {

  private var stateValue: ProcessingTypeDataState[TT, CC] = transformState(observed.state)

  override private[processingtypedata] def state: ProcessingTypeDataState[TT, CC] = synchronized {
    val currentObservedState = observed.state
    if (currentObservedState.stateIdentity != stateValue.stateIdentity) {
      stateValue = transformState(currentObservedState)
    }
    stateValue
  }

}

object ProcessingTypeDataProvider {

  val noCombinedDataFun: () => Nothing = () =>
    throw new IllegalStateException(
      "Processing type data provider does not have combined data!"
    )

  def apply[T, C](stateValue: ProcessingTypeDataState[T, C]): ProcessingTypeDataProvider[T, C] =
    new ProcessingTypeDataProvider[T, C] {
      override private[processingtypedata] def state: ProcessingTypeDataState[T, C] = stateValue
    }

  def apply[T, C](
      allValues: Map[ProcessingType, ValueWithPermission[T]],
      combinedValue: C
  ): ProcessingTypeDataProvider[T, C] =
    new ProcessingTypeDataProvider[T, C] {

      override private[processingtypedata] val state: ProcessingTypeDataState[T, C] = ProcessingTypeDataState(
        allValues,
        () => combinedValue,
        allValues
      )

    }

  def withEmptyCombinedData[T](
      allValues: Map[ProcessingType, ValueWithPermission[T]]
  ): ProcessingTypeDataProvider[T, Nothing] =
    new ProcessingTypeDataProvider[T, Nothing] {

      override private[processingtypedata] val state: ProcessingTypeDataState[T, Nothing] = ProcessingTypeDataState(
        allValues,
        noCombinedDataFun,
        allValues
      )

    }

}

trait ProcessingTypeDataState[+T, +C] {
  def all: Map[ProcessingType, ValueWithPermission[T]]

  // It returns function because we want to sometimes throw Exception instead of return value and we want to
  // transform values without touch combined part
  def getCombined: () => C

  // We keep stateIdentity as a separate value to avoid frequent computation of this.all.equals(that.all)
  // Also, it is easier to provide one (source) state identity than provide it for all observers
  def stateIdentity: Any

  final def mapValues[TT](fun: T => TT): ProcessingTypeDataState[TT, C] =
    ProcessingTypeDataState[TT, C](all.mapValuesNow(_.map(fun)), getCombined, stateIdentity)

  final def mapCombined[CC](fun: C => CC): ProcessingTypeDataState[T, CC] = {
    val newCombined = fun(getCombined())
    ProcessingTypeDataState[T, CC](all, () => newCombined, stateIdentity)
  }

}

object ProcessingTypeDataState {

  def apply[T, C](
      allValues: Map[ProcessingType, ValueWithPermission[T]],
      getCombinedValue: () => C,
      stateIdentityValue: Any
  ): ProcessingTypeDataState[T, C] =
    new ProcessingTypeDataState[T, C] {
      override def all: Map[ProcessingType, ValueWithPermission[T]] = allValues
      override def getCombined: () => C                             = getCombinedValue
      override def stateIdentity: Any                               = stateIdentityValue
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

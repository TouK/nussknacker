package pl.touk.nussknacker.ui.process.processingtype.provider

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.util.concurrent.atomic.AtomicReference

/**
  * ProcessingType is a context of application. One ProcessingType can't see data from another ProcessingType.
  * Services run inside one ProcessingType scope behave differently from services run in another scope.
  *
  * This class is meant to provide access to some scope of data inside context of application to the user.
  * We don't want to pass all ProcessingType's data to every service because it would complicate testing of services
  * and would broke isolation between areas of application. Due to that, this class is a `Functor`
  * (to be precise `BiFunctor` but more on that below) which allows to transform the scope of `Data`.
  *
  * Sometimes it is necessary to have access also to combination of data across all ProcessingTypes. Due to that
  * this class is a `BiFunctor` which second value named as `CombinedData`
  *
  * This class caches `Data` and `CombinedData` wrapped in `ProcessingTypeDataState` to avoid computations of
  * transformations during each lookup to `Data`/`CombinedData`. It behave similar to `Observable` where given
  * transformed `ProcessingTypeDataProvider` check its parent if `ProcessingTypeDataState.stateIdentity` changed.
  *
  * ProcessingType is associated with Category e.g. Fraud Detection, Marketing. Given user has access to certain
  * categories see `LoggedUser.can`. Due to that, during each access to `Data`, user is authorized if he/she
  * has access to category.
  */
trait ProcessingTypeDataProvider[+Data, +CombinedData] {

  // TODO: replace with proper forType handling
  final def forProcessingTypeUnsafe(processingType: ProcessingType)(implicit user: LoggedUser): Data =
    forProcessingType(processingType)
      .getOrElse(
        throw new IllegalArgumentException(
          s"Unknown ProcessingType: $processingType, known ProcessingTypes are: ${all.keys.mkString(", ")}"
        )
      )

  final def forProcessingType(processingType: ProcessingType)(implicit user: LoggedUser): Option[Data] = {
    allAuthorized
      .get(processingType)
      .map(_.getOrElse(throw new UnauthorizedError(user)))
  }

  final def forProcessingTypeE(
      processingType: ProcessingType
  )(implicit user: LoggedUser): Either[UnauthorizedError, Option[Data]] = {
    allAuthorized
      .get(processingType) match {
      case Some(dataO) =>
        dataO match {
          case Some(data) => Right(Some(data))
          case None       => Left(new UnauthorizedError(user))
        }
      case None => Right(None)
    }
  }

  final def forProcessingTypeEUnsafe(
      processingType: ProcessingType
  )(implicit user: LoggedUser): Either[UnauthorizedError, Data] = {
    allAuthorized
      .get(processingType) match {
      case Some(dataO) =>
        dataO match {
          case Some(data) => Right(data)
          case None       => Left(new UnauthorizedError(user))
        }
      case None =>
        throw new IllegalStateException(
          s"Error while providing process resolver for processing type $processingType requested by user ${user.username}"
        )
    }
  }

  final def all(implicit user: LoggedUser): Map[ProcessingType, Data] = allAuthorized.collect { case (k, Some(v)) =>
    (k, v)
  }

  private def allAuthorized(implicit user: LoggedUser): Map[ProcessingType, Option[Data]] =
    state.all.mapValuesNow(_.valueWithAllowedAccess(Permission.Read))

  // TODO: We should return a generic type that can produce views for users with access rights to certain categories only.
  //       Thanks to that we will be sure that no sensitive data leak
  final def combined: CombinedData = state.getCombined()

  private[processingtype] def state: ProcessingTypeDataState[Data, CombinedData]

  final def mapValues[TT](fun: Data => TT): ProcessingTypeDataProvider[TT, CombinedData] =
    new TransformingProcessingTypeDataProvider[Data, CombinedData, TT, CombinedData](this, _.mapValues(fun))

  final def mapCombined[CC](fun: CombinedData => CC): ProcessingTypeDataProvider[Data, CC] =
    new TransformingProcessingTypeDataProvider[Data, CombinedData, Data, CC](this, _.mapCombined(fun))

}

private[processingtype] class TransformingProcessingTypeDataProvider[T, C, TT, CC](
    observed: ProcessingTypeDataProvider[T, C],
    transformState: ProcessingTypeDataState[T, C] => ProcessingTypeDataState[TT, CC]
) extends ProcessingTypeDataProvider[TT, CC] {

  private val stateValue = new AtomicReference(transformState(observed.state))

  override private[processingtype] def state: ProcessingTypeDataState[TT, CC] = {
    stateValue.updateAndGet { currentValue =>
      val currentObservedState = observed.state
      if (currentObservedState.stateIdentity != currentValue.stateIdentity) {
        transformState(currentObservedState)
      } else {
        currentValue
      }
    }
  }

}

object ProcessingTypeDataProvider {

  val noCombinedDataFun: () => Nothing = () =>
    throw new IllegalStateException(
      "Processing type data provider does not have combined data!"
    )

  def apply[T, C](stateValue: ProcessingTypeDataState[T, C]): ProcessingTypeDataProvider[T, C] =
    new ProcessingTypeDataProvider[T, C] {
      override private[processingtype] def state: ProcessingTypeDataState[T, C] = stateValue
    }

  def apply[T, C](
      allValues: Map[ProcessingType, ValueWithRestriction[T]],
      combinedValue: C
  ): ProcessingTypeDataProvider[T, C] =
    new ProcessingTypeDataProvider[T, C] {

      override private[processingtype] val state: ProcessingTypeDataState[T, C] = ProcessingTypeDataState(
        allValues,
        () => combinedValue,
        allValues
      )

    }

  def withEmptyCombinedData[T](
      allValues: Map[ProcessingType, ValueWithRestriction[T]]
  ): ProcessingTypeDataProvider[T, Nothing] =
    new ProcessingTypeDataProvider[T, Nothing] {

      override private[processingtype] val state: ProcessingTypeDataState[T, Nothing] = ProcessingTypeDataState(
        allValues,
        noCombinedDataFun,
        allValues
      )

    }

}

// It keeps a state (Data and CombinedData) that is cached and restricted by ProcessingTypeDataProvider
trait ProcessingTypeDataState[+Data, +CombinedData] {
  def all: Map[ProcessingType, ValueWithRestriction[Data]]

  // It returns function because we want to sometimes throw Exception instead of return value and we want to
  // transform values without touch combined part
  def getCombined: () => CombinedData

  // We keep stateIdentity as a separate value to avoid frequent computation of this.all.equals(that.all)
  // Also, it is easier to provide one (source) state identity than provide it for all observers
  def stateIdentity: Any

  final def mapValues[TT](fun: Data => TT): ProcessingTypeDataState[TT, CombinedData] =
    ProcessingTypeDataState[TT, CombinedData](all.mapValuesNow(_.map(fun)), getCombined, stateIdentity)

  final def mapCombined[CC](fun: CombinedData => CC): ProcessingTypeDataState[Data, CC] = {
    val newCombined = fun(getCombined())
    ProcessingTypeDataState[Data, CC](all, () => newCombined, stateIdentity)
  }

}

object ProcessingTypeDataState {

  def apply[Data, CombinedData](
      allValues: Map[ProcessingType, ValueWithRestriction[Data]],
      getCombinedValue: () => CombinedData,
      stateIdentityValue: Any
  ): ProcessingTypeDataState[Data, CombinedData] =
    new ProcessingTypeDataState[Data, CombinedData] {
      override def all: Map[ProcessingType, ValueWithRestriction[Data]] = allValues
      override def getCombined: () => CombinedData                      = getCombinedValue
      override def stateIdentity: Any                                   = stateIdentityValue
    }

}

package pl.touk.nussknacker.ui.process.processingtype.provider

import cats.effect.std.Mutex
import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Ref}
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.util.concurrent.CopyOnWriteArrayList
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

/**
  * ProcessingType is a context of application. One ProcessingType can't see data from another ProcessingType.
  * Services run inside one ProcessingType scope behave differently from services run in another scope.
  *
  * This class is meant to provide access to some scope of data inside context of application to the user.
  * We don't want to pass all ProcessingType's data to every service because it would complicate testing of services
  * and would brake isolation between areas of application. Due to that, this class is a `Functor`
  * (to be precise `BiFunctor` but more on that below) which allows to transform the scope of `Data`.
  *
  * Sometimes it is necessary to have access also to combination of data across all ProcessingTypes. Due to that
  * this class is a `BiFunctor` which second value named as `CombinedData`
  *
  * This class caches `Data` and `CombinedData` wrapped in `ProcessingTypeDataState` to avoid computations of
  * transformations during each lookup to `Data`/`CombinedData`.
  *
  * ProcessingType is associated with Category e.g. Fraud Detection, Marketing. Given user has access to certain
  * categories see `LoggedUser.can`. Due to that, during each access to `Data`, user is authorized if their
  * has access to category.
  */
abstract class ProcessingTypeDataProvider[Data, CombinedData](initialState: ProcessingTypeDataState[Data, CombinedData])
    extends LazyLogging {

  // TODO: get rid of unsafeRunSync for Ref and Mutex
  protected val stateRef: Ref[IO, ProcessingTypeDataState[Data, CombinedData]] =
    Ref.of[IO, ProcessingTypeDataState[Data, CombinedData]](initialState).unsafeRunSync()(IORuntime.global)

  protected val stateMutex: Mutex[IO] = Mutex[IO].unsafeRunSync()(IORuntime.global)

  private val observers = new CopyOnWriteArrayList[Observer[ProcessingTypeDataState[Data, CombinedData]]]()

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
  final def combined: CombinedData = state.combinedDataTry.get

  final protected[provider] def setStateValueAndNotifyObservers(
      newValue: ProcessingTypeDataState[Data, CombinedData]
  ): IO[Unit] = {
    for {
      _ <- stateRef.set(newValue)
      _ <- observers.asScala
        .map { observer =>
          observer.notifyChange(newValue)
        }
        .toList
        .sequence
    } yield ()
  }

  private def state: ProcessingTypeDataState[Data, CombinedData] = {
    stateMutex.lock
      .surround {
        stateRef.get
      }
      .unsafeRunSync()(IORuntime.global) // TODO: get rid of unsafeRunSync
  }

  final def mapValues[TT](fun: Data => TT): ProcessingTypeDataProvider[TT, CombinedData] = {
    val childProvider =
      new TransformingProcessingTypeDataProvider[Data, CombinedData, TT, CombinedData](this.state, _.mapValues(fun))
    observers.add(childProvider)
    childProvider
  }

  final def mapCombined[CC](fun: CombinedData => CC): ProcessingTypeDataProvider[Data, CC] = {
    val childProvider =
      new TransformingProcessingTypeDataProvider[Data, CombinedData, Data, CC](this.state, _.mapCombined(fun))
    observers.add(childProvider)
    childProvider
  }

}

trait Observer[V] {
  def notifyChange(newValue: V): IO[Unit]
}

private[provider] class TransformingProcessingTypeDataProvider[T, C, TT, CC](
    initialValue: ProcessingTypeDataState[T, C],
    transformState: ProcessingTypeDataState[T, C] => ProcessingTypeDataState[TT, CC]
) extends ProcessingTypeDataProvider[TT, CC](transformState(initialValue))
    with Observer[ProcessingTypeDataState[T, C]] {

  override def notifyChange(newValue: ProcessingTypeDataState[T, C]): IO[Unit] = {
    stateMutex.lock.surround {
      setStateValueAndNotifyObservers(transformState(newValue))
    }
  }

}

// It keeps a state (Data and CombinedData) that is cached and restricted by ProcessingTypeDataProvider
final class ProcessingTypeDataState[+Data, +CombinedData](
    private[provider] val all: Map[ProcessingType, ValueWithRestriction[Data]],
    private[processingtype] val combinedDataTry: Try[CombinedData]
) {

  def mapValues[TT](fun: Data => TT): ProcessingTypeDataState[TT, CombinedData] =
    new ProcessingTypeDataState[TT, CombinedData](all.mapValuesNow(_.map(fun)), combinedDataTry)

  def mapCombined[CC](fun: CombinedData => CC): ProcessingTypeDataState[Data, CC] = {
    val newCombined = combinedDataTry.map(fun)
    new ProcessingTypeDataState[Data, CC](all, newCombined)
  }

}

object ProcessingTypeDataState {

  val uninitialized = new ProcessingTypeDataState(
    all = Map.empty,
    combinedDataTry = Failure(new IllegalAccessException("ProcessingTypeData is not initialized"))
  )

}

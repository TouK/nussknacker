package pl.touk.nussknacker.test.utils.domain

import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.{ProcessingTypeDataProvider, ProcessingTypeDataState}

import scala.util.{Failure, Success}

object TestProcessingTypeDataProviderFactory {

  def withIdentityFromValues[T, C](
      allValues: Map[ProcessingType, ValueWithRestriction[T]],
      combinedValue: C
  ): ProcessingTypeDataProvider[T, C] =
    fromState(
      new ProcessingTypeDataState(
        allValues,
        Success(combinedValue),
        allValues
      )
    )

  def withEmptyCombinedData[T](
      allValues: Map[ProcessingType, ValueWithRestriction[T]]
  ): ProcessingTypeDataProvider[T, Nothing] =
    fromState(
      new ProcessingTypeDataState(
        allValues,
        Failure(
          new IllegalStateException(
            "Processing type data provider does not have combined data!"
          )
        ),
        allValues
      )
    )

  def fromState[T, C](stateValue: ProcessingTypeDataState[T, C]): ProcessingTypeDataProvider[T, C] =
    new ProcessingTypeDataProvider[T, C] {
      override def state: ProcessingTypeDataState[T, C] = stateValue
    }

}

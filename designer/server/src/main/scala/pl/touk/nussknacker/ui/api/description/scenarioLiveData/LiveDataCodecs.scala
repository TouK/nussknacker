package pl.touk.nussknacker.ui.api.description.scenarioLiveData

import io.circe.Codec
import io.circe.derivation.deriveCodec
import pl.touk.nussknacker.ui.api.description.scenarioLiveData.Dtos.{
  ExceptionResultDto,
  InvocationResultDto,
  LiveDataDto,
  LiveDataForNodeTransitionDto,
  LiveDataSampleDto
}

object LiveDataCodecs {

  implicit def exceptionResultDtoCodec: Codec[ExceptionResultDto]                     = deriveCodec
  implicit def invocationResultDtoCodec: Codec[InvocationResultDto]                   = deriveCodec
  implicit def liveDataSampleDtoCodec: Codec[LiveDataSampleDto]                       = deriveCodec
  implicit def liveDataForNodeTransitionDtoCodec: Codec[LiveDataForNodeTransitionDto] = deriveCodec
  implicit def resultsWithCountsCodec: Codec[LiveDataDto]                             = deriveCodec

}

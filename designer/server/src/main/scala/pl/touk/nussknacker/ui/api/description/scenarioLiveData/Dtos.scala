package pl.touk.nussknacker.ui.api.description.scenarioLiveData

import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import sttp.tapir.{Codec, CodecFormat}

object Dtos {

  sealed trait LiveDataError

  object LiveDataError {

    case object NoPermission extends LiveDataError with CustomAuthorizationError

    case object NoScenario extends LiveDataError

    implicit val noScenarioErrorCodec: Codec[String, NoScenario.type, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario.type] { _ =>
        s"Scenario not found"
      }

    case object LiveDataNotSupported extends LiveDataError

    implicit val liveDataNotSupportedErrorCodec: Codec[String, LiveDataNotSupported.type, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[LiveDataNotSupported.type] { _ =>
        s"Live data preview is not supported by this scenario"
      }

    case object LiveDataNotAvailable extends LiveDataError

    implicit val liveDataNotAvailableErrorCodec: Codec[String, LiveDataNotAvailable.type, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[LiveDataNotAvailable.type] { _ =>
        s"There is currently no live data available for this scenario"
      }

  }

}

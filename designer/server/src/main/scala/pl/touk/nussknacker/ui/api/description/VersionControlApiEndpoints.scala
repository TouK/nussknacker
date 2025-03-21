package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints.VersionControlError
import pl.touk.nussknacker.ui.api.description.VersionControlApiEndpoints.VersionControlError.{
  MissingProcessId,
  MissingProcessVersion
}
import sttp.model.StatusCode.BadRequest
import sttp.tapir._
import sttp.tapir.EndpointIO.Example
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe._

class VersionControlApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import VersionControlApiEndpoints.Dtos._

  lazy val versionValidationEndpoint: SecuredEndpoint[
    (ProcessName, ProcessVersionValidationRequestDto),
    VersionControlError,
    ProcessVersionValidationResponseDto,
    Any
  ] =
    baseNuApiEndpoint
      .summary("Checks if local scenario version is the newest one")
      .tag("Version Control")
      .post
      .in("versionControl" / path[ProcessName]("scenarioName") / "versionValidation")
      .in(
        jsonBody[ProcessVersionValidationRequestDto]
          .example(
            ProcessVersionValidationRequestDto(
              localVersion = 24
            )
          )
      )
      .out(
        jsonBody[ProcessVersionValidationResponseDto]
          .examples(
            List(
              Example.of(
                name = Some("Newer process version is available"),
                value = ProcessVersionValidationResponseDto(
                  processName = "p1",
                  isLatest = false,
                  localVersion = 51,
                  latestDeployedVersion = None,
                  latestVersion = 52,
                  modifiedBy = "nussknacker"
                )
              ),
              Example.of(
                name = Some("Process local version is the latest one"),
                value = ProcessVersionValidationResponseDto(
                  processName = "p2",
                  isLatest = true,
                  localVersion = 52,
                  latestDeployedVersion = Some(48),
                  latestVersion = 52,
                  modifiedBy = "nussknacker"
                )
              )
            )
          )
      )
      .errorOut(
        oneOf[VersionControlError](
          oneOfVariant(
            BadRequest,
            plainBody[MissingProcessVersion]
          ),
          oneOfVariant(
            BadRequest,
            plainBody[MissingProcessId]
          )
        )
      )
      .withSecurity(auth)

}

object VersionControlApiEndpoints {

  object Dtos {

    @derive(schema, encoder, decoder)
    final case class ProcessVersionValidationRequestDto(localVersion: Int)

    @derive(schema, encoder, decoder)
    final case class ProcessVersionValidationResponseDto(
        processName: String,
        isLatest: Boolean,
        localVersion: Long,
        latestDeployedVersion: Option[Long],
        latestVersion: Long,
        modifiedBy: String
    )

  }

  sealed trait VersionControlError

  object VersionControlError {
    case class MissingProcessVersion(processName: String) extends VersionControlError
    case class MissingProcessId(processName: String)      extends VersionControlError

    implicit val missingProcessVersionCodec: Codec[String, MissingProcessVersion, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[MissingProcessVersion](e =>
        s"Missing scenario process version for process: ${e.processName}"
      )

    implicit val missingProcessIdCodec: Codec[String, MissingProcessId, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[MissingProcessId](e =>
        s"Missing process id for scenario with name: ${e.processName}"
      )

  }

}

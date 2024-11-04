package pl.touk.nussknacker.ui.api.description

import io.circe.Encoder
import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.description.StickyNotesApiEndpoints.Examples.noScenarioExample
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNotesError
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNotesError.NoScenario
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import java.time.Instant
import java.util.UUID

class StickyNotesApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import stickynotes.Dtos._
  import TapirCodecs.VersionIdCodec._

  lazy val encoder: Encoder[TypingResult] = TypingResult.encoder

  private val exampleInstantDate = Instant.ofEpochMilli(1730136602)

  val exampleStickyNote = StickyNote(
    1,
    UUID.fromString("3fa77f68-5717-4562-b3fc-2c942f99afa5"),
    "##Title \nNote1",
    LayoutData(20, 30),
    "#99aa20",
    None,
    "Marco",
    exampleInstantDate
  )

  lazy val stickyNotesGetEndpoint: SecuredEndpoint[
    (ProcessName, VersionId),
    StickyNotesError,
    List[StickyNote],
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Returns sticky nodes for given scenario with version")
      .tag("StickyNotes")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "stickyNotes" / query[VersionId]("scenarioVersionId"))
      .out(
        statusCode(Ok).and(
          jsonBody[List[StickyNote]]
            .examples(
              List(
                Example.of(
                  summary = Some("List of valid sticky notes returned for scenario"),
                  value = List(
                    exampleStickyNote,
                    exampleStickyNote.copy(id = 2, noteId = UUID.fromString("3fa77f68-5717-4562-b3fc-2c942f99afc7"))
                  )
                ) // TODO example of errors
              )
            )
        )
      )
      .errorOut(
        oneOf[StickyNotesError](
          noScenarioExample
        )
      )
      .withSecurity(auth)
  }

  lazy val stickyNotesAddEndpoint
      : SecuredEndpoint[(ProcessName, StickyNoteRequestBody), StickyNotesError, Unit, Any] = {
    baseNuApiEndpoint
      .summary("Creates new sticky note with given content")
      .tag("StickyNotes")
      .post
      .in("processes" / path[ProcessName]("scenarioName") / "stickyNotes")
      .in(
        jsonBody[StickyNoteRequestBody]
          .example(StickyNoteRequestBody(None, VersionId(1), "", LayoutData(12, 33), "#441022", None))
      )
      .out(
        statusCode(Ok)
      )
      .errorOut(
        oneOf[StickyNotesError](
          noScenarioExample
        )
      )
      .withSecurity(auth)
  }

}

object StickyNotesApiEndpoints {

  object Examples {

    val noScenarioExample: EndpointOutput.OneOfVariant[NoScenario] =
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoScenario]
          .example(
            Example.of(
              summary = Some("No scenario {scenarioName} found"),
              value = NoScenario(ProcessName("s1"))
            )
          )
      )

  }

}

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
import pl.touk.nussknacker.ui.api.description.StickyNotesApiEndpoints.Examples.{noScenarioExample, noStickyNoteExample}
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNoteId
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNotesError.{NoScenario, NoStickyNote}
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

  private val exampleStickyNote = StickyNote(
    StickyNoteId(1),
    "##Title \nNote1",
    LayoutData(20, 30),
    "#99aa20",
    Dimensions(300, 200),
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
                    exampleStickyNote.copy(noteId = StickyNoteId(2))
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

  lazy val stickyNotesUpdateEndpoint
      : SecuredEndpoint[(ProcessName, StickyNoteUpdateRequest), StickyNotesError, Unit, Any] = {
    baseNuApiEndpoint
      .summary("Updates sticky note with new values")
      .tag("StickyNotes")
      .put
      .in("processes" / path[ProcessName]("scenarioName") / "stickyNotes")
      .in(
        jsonBody[StickyNoteUpdateRequest]
          .example(
            StickyNoteUpdateRequest(
              StickyNoteId(1),
              VersionId(1),
              "",
              LayoutData(12, 33),
              "#441022",
              Dimensions(300, 200),
              None
            )
          )
      )
      .out(
        statusCode(Ok)
      )
      .errorOut(
        oneOf[StickyNotesError](
          noScenarioExample,
          noStickyNoteExample
        )
      )
      .withSecurity(auth)
  }

  lazy val stickyNotesAddEndpoint
      : SecuredEndpoint[(ProcessName, StickyNoteAddRequest), StickyNotesError, StickyNoteCorrelationId, Any] = {
    baseNuApiEndpoint
      .summary("Creates new sticky note with given content")
      .tag("StickyNotes")
      .post
      .in("processes" / path[ProcessName]("scenarioName") / "stickyNotes")
      .in(
        jsonBody[StickyNoteAddRequest]
          .example(StickyNoteAddRequest(VersionId(1), "", LayoutData(12, 33), "#441022", Dimensions(300, 200), None))
      )
      .out(jsonBody[StickyNoteCorrelationId])
      .errorOut(
        oneOf[StickyNotesError](
          noScenarioExample
        )
      )
      .withSecurity(auth)
  }

  lazy val stickyNotesDeleteEndpoint: SecuredEndpoint[(ProcessName, StickyNoteId), StickyNotesError, Unit, Any] = {
    baseNuApiEndpoint
      .summary("Deletes stickyNote by given noteId")
      .tag("StickyNotes")
      .delete
      .in("processes" / path[ProcessName]("scenarioName") / "stickyNotes" / path[StickyNoteId]("noteId"))
      .out(
        statusCode(Ok)
      )
      .errorOut(
        oneOf[StickyNotesError](
          noStickyNoteExample,
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

    val noStickyNoteExample: EndpointOutput.OneOfVariant[NoStickyNote] =
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoStickyNote]
          .example(
            Example.of(
              summary = Some("No sticky note with id: 3 was found"),
              value = NoStickyNote(StickyNoteId(3))
            )
          )
      )

  }

}

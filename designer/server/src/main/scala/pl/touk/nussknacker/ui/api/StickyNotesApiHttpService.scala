package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.description.StickyNotesApiEndpoints
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.{
  StickyNote,
  StickyNoteAddRequest,
  StickyNoteCorrelationId,
  StickyNoteId,
  StickyNotesError,
  StickyNotesSettings,
  StickyNoteUpdateRequest
}
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNotesError.{
  NoPermission,
  NoScenario,
  StickyNoteContentTooLong,
  StickyNoteCountLimitReached
}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.stickynotes.StickyNotesRepository
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class StickyNotesApiHttpService(
    authManager: AuthManager,
    stickyNotesRepository: StickyNotesRepository,
    scenarioService: ProcessService,
    scenarioAuthorizer: AuthorizeProcess,
    dbioActionRunner: DBIOActionRunner,
    stickyNotesSettings: StickyNotesSettings
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val securityInput = authManager.authenticationEndpointInput()

  private val endpoints = new StickyNotesApiEndpoints(securityInput)

  expose {
    endpoints.stickyNotesGetEndpoint
      .serverSecurityLogic(authorizeKnownUser[StickyNotesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, versionId) =>
          for {
            scenarioId      <- getScenarioIdByName(scenarioName)
            _               <- isAuthorized(scenarioId, Permission.Read)
            processActivity <- fetchStickyNotes(scenarioId, versionId)
          } yield processActivity.toList
        }
      }
  }

  expose {
    endpoints.stickyNotesAddEndpoint
      .serverSecurityLogic(authorizeKnownUser[StickyNotesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, requestBody) =>
          for {
            scenarioId      <- getScenarioIdByName(scenarioName)
            _               <- isAuthorized(scenarioId, Permission.Write)
            count           <- getStickyNotesCount(scenarioId, requestBody.scenarioVersionId)
            _               <- validateStickyNotesCount(count, stickyNotesSettings)
            _               <- validateStickyNoteContent(requestBody.content, stickyNotesSettings)
            processActivity <- addStickyNote(scenarioId, requestBody)
          } yield processActivity
        }
      }
  }

  expose {
    endpoints.stickyNotesUpdateEndpoint
      .serverSecurityLogic(authorizeKnownUser[StickyNotesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, requestBody) =>
          for {
            scenarioId      <- getScenarioIdByName(scenarioName)
            _               <- isAuthorized(scenarioId, Permission.Write)
            _               <- validateStickyNoteContent(requestBody.content, stickyNotesSettings)
            processActivity <- updateStickyNote(requestBody)
          } yield processActivity.toInt
        }
      }
  }

  expose {
    endpoints.stickyNotesDeleteEndpoint
      .serverSecurityLogic(authorizeKnownUser[StickyNotesError])
      .serverLogicEitherT { implicit loggedUser =>
        { case (scenarioName, noteId) =>
          for {
            scenarioId      <- getScenarioIdByName(scenarioName)
            _               <- isAuthorized(scenarioId, Permission.Write)
            processActivity <- deleteStickyNote(noteId)
          } yield processActivity.toInt
        }
      }

  }

  private def getScenarioIdByName(scenarioName: ProcessName) = {
    EitherT.fromOptionF(
      scenarioService.getProcessId(scenarioName),
      NoScenario(scenarioName)
    )
  }

  private def isAuthorized(scenarioId: ProcessId, permission: Permission)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, StickyNotesError, Unit] =
    EitherT(
      scenarioAuthorizer
        .check(scenarioId, permission, loggedUser)
        .map[Either[StickyNotesError, Unit]] {
          case true  => Right(())
          case false => Left(NoPermission)
        }
    )

  private def fetchStickyNotes(scenarioId: ProcessId, versionId: VersionId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, StickyNotesError, Seq[StickyNote]] =
    EitherT
      .right(
        dbioActionRunner.run(
          stickyNotesRepository.findStickyNotes(scenarioId, versionId)
        )
      )

  private def getStickyNotesCount(scenarioId: ProcessId, versionId: VersionId): EitherT[Future, StickyNotesError, Int] =
    EitherT
      .right(
        dbioActionRunner
          .run(
            stickyNotesRepository.countStickyNotes(scenarioId, versionId)
          )
      )

  private def deleteStickyNote(noteId: StickyNoteId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, StickyNotesError, Int] =
    for {
      note <- EitherT.fromOptionF(
        dbioActionRunner.run(
          stickyNotesRepository.findStickyNoteById(noteId)
        ),
        StickyNotesError.NoStickyNote(noteId)
      )
      _ <- isAuthorized(note.scenarioId, Permission.Write)
      result <- EitherT.right(
        dbioActionRunner.run(
          stickyNotesRepository.deleteStickyNote(noteId)
        )
      )
    } yield result

  private def validateStickyNotesCount(
      stickyNotesCount: Int,
      stickyNotesConfig: StickyNotesSettings
  ): EitherT[Future, StickyNotesError, Unit] =
    EitherT.fromEither(
      Either.cond(
        stickyNotesCount < stickyNotesConfig.maxNotesCount,
        (),
        StickyNoteCountLimitReached(stickyNotesConfig.maxNotesCount)
      )
    )

  private def addStickyNote(scenarioId: ProcessId, requestBody: StickyNoteAddRequest)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, StickyNotesError, StickyNoteCorrelationId] =
    EitherT
      .right(
        dbioActionRunner.run(
          stickyNotesRepository.addStickyNote(
            requestBody.content,
            requestBody.layoutData,
            requestBody.color,
            requestBody.dimensions,
            requestBody.targetEdge,
            scenarioId,
            requestBody.scenarioVersionId
          )
        )
      )

  private def validateStickyNoteContent(
      content: String,
      stickyNotesConfig: StickyNotesSettings
  ): EitherT[Future, StickyNotesError, Unit] =
    EitherT.fromEither(
      Either.cond(
        content.length <= stickyNotesConfig.maxContentLength,
        (),
        StickyNoteContentTooLong(content.length, stickyNotesConfig.maxContentLength)
      )
    )

  private def updateStickyNote(requestBody: StickyNoteUpdateRequest)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, StickyNotesError, Int] = for {
    note <- EitherT.fromOptionF(
      dbioActionRunner.run(
        stickyNotesRepository.findStickyNoteById(requestBody.noteId)
      ),
      StickyNotesError.NoStickyNote(requestBody.noteId)
    )
    _ <- isAuthorized(note.scenarioId, Permission.Write)
    result <- EitherT.right(
      dbioActionRunner.run(
        stickyNotesRepository.updateStickyNote(
          requestBody.noteId,
          requestBody.content,
          requestBody.layoutData,
          requestBody.color,
          requestBody.dimensions,
          requestBody.targetEdge,
          requestBody.scenarioVersionId
        )
      )
    )
  } yield result

}

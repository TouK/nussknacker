package pl.touk.nussknacker.ui.api.description.scenarioActivity

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError.{NoComment, NoScenario}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.{
  Attachment,
  Comment,
  FoundActivity,
  ScenarioActivities,
  ScenarioActivitiesSearchResult,
  ScenarioActivity,
  ScenarioAttachments,
  ScenarioCommentsAndAttachments
}
import sttp.tapir.EndpointIO.Example

import java.time.Instant
import java.util.UUID

object Examples {

  val scenarioCommentsAndAttachments: Example[ScenarioCommentsAndAttachments] = Example.of(
    summary = Some("Display scenario activity"),
    value = ScenarioCommentsAndAttachments(
      comments = List(
        Comment(
          id = 1L,
          scenarioVersion = 1L,
          content = "some comment",
          user = "test",
          createDate = Instant.parse("2024-01-17T14:21:17Z")
        )
      ),
      attachments = List(
        Attachment(
          id = 1L,
          scenarioVersion = 1L,
          fileName = "some_file.txt",
          user = "test",
          createDate = Instant.parse("2024-01-17T14:21:17Z")
        )
      )
    )
  )

  val scenarioActivities: Example[ScenarioActivities] = Example.of(
    summary = Some("Display scenario actions"),
    value = ScenarioActivities(
      activities = List(
        ScenarioActivity.forScenarioCreated(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = None,
        ),
        ScenarioActivity.forScenarioArchived(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = None,
        ),
        ScenarioActivity.forScenarioUnarchived(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = None,
        ),
        ScenarioActivity.forScenarioDeployed(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = Some("Deployment of scenario - task JIRA-1234"),
        ),
        ScenarioActivity.forScenarioCanceled(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = Some("Canceled because marketing campaign ended"),
        ),
        ScenarioActivity.forScenarioModified(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = Some("Added new processing step"),
        ),
        ScenarioActivity.forScenarioNameChanged(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = None,
          oldName = "marketing campaign",
          newName = "old marketing campaign",
        ),
        ScenarioActivity.forCommentAdded(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = Some("Now scenario handles errors in datasource better"),
        ),
        ScenarioActivity.forCommentAddedAndDeleted(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = None,
          deletedByUser = "John Doe",
        ),
        ScenarioActivity.forAttachmentAdded(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = None,
          attachmentId = "10000001",
          attachmentFilename = "attachment01.png"
        ),
        ScenarioActivity.forAttachmentAddedAndDeleted(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = None,
          deletedByUser = "John Doe",
        ),
        ScenarioActivity.forChangedProcessingMode(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = None,
          from = "Request-Response",
          to = "Batch",
        ),
        ScenarioActivity.forIncomingMigration(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = Some("Migration from preprod"),
          sourceEnvironment = "preprod",
          sourceScenarioVersion = "23",
        ),
        ScenarioActivity.forOutgoingMigration(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = Some("Migration to preprod"),
          destinationEnvironment = "preprod",
        ),
        ScenarioActivity.forPerformedSingleExecution(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          comment = None,
          dateFinished = "2024-01-17T14:21:17Z",
          status = "Successfully executed",
        ),
        ScenarioActivity.forPerformedScheduledExecution(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          dateFinished = "2024-01-17T14:21:17Z",
          comment = None,
          params = "Batch size=1",
          status = "Successfully executed",
        ),
        ScenarioActivity.forAutomaticUpdate(
          id = UUID.randomUUID().toString,
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = 1,
          dateFinished = "2024-01-17T14:21:17Z",
          comment = None,
          changes = "JIRA-12345, JIRA-32146",
          status = "Successful",
        ),
      ),
    )
  )

  val scenarioActivitiesSearchResult: Example[ScenarioActivitiesSearchResult] = Example.of(
    summary = Some("Result of activities full-text search"),
    value = ScenarioActivitiesSearchResult(
      foundActivities = List(
        FoundActivity(id = UUID.randomUUID().toString, index = 101),
        FoundActivity(id = UUID.randomUUID().toString, index = 498)
      )
    )
  )

  val scenarioAttachments: Example[ScenarioAttachments] = Example.of(
    summary = Some("Display scenario activity"),
    value = ScenarioAttachments(
      attachments = List(
        Attachment(
          id = 1L,
          scenarioVersion = 1L,
          fileName = "some_file.txt",
          user = "test",
          createDate = Instant.parse("2024-01-17T14:21:17Z")
        )
      )
    )
  )

  val noScenarioError: Example[NoScenario] = Example.of(
    summary = Some("No scenario {scenarioName} found"),
    value = NoScenario(ProcessName("'example scenario'"))
  )

  val commentNotFoundError: Example[NoComment] = Example.of(
    summary = Some("Unable to edit comment with id: {commentId}"),
    value = NoComment(1L)
  )

}

package pl.touk.nussknacker.ui.api.description.scenarioActivity

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError.{NoComment, NoScenario}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos._
import sttp.tapir.EndpointIO.Example

import java.time.Instant
import java.util.UUID

object Examples {

  val deprecatedScenarioActivity: Example[Legacy.ProcessActivity] = Example.of(
    summary = Some("Display scenario activity"),
    value = Legacy.ProcessActivity(
      comments = List(
        Legacy.Comment(
          id = 1L,
          processVersionId = 1L,
          content = "some comment",
          user = "test",
          createDate = Instant.parse("2024-01-17T14:21:17Z")
        )
      ),
      attachments = List(
        Legacy.Attachment(
          id = 1L,
          processVersionId = 1L,
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
        ScenarioActivity.ScenarioCreated(
          id = UUID.fromString("80c95497-3b53-4435-b2d9-ae73c5766213"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
        ),
        ScenarioActivity.ScenarioArchived(
          id = UUID.fromString("070a4e5c-21e5-4e63-acac-0052cf705a90"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
        ),
        ScenarioActivity.ScenarioUnarchived(
          id = UUID.fromString("fa35d944-fe20-4c4f-96c6-316b6197951a"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
        ),
        ScenarioActivity.ScenarioDeployed(
          id = UUID.fromString("545b7d87-8cdf-4cb5-92c4-38ddbfca3d08"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          comment = ScenarioActivityComment(
            comment = Some("Deployment of scenario - task JIRA-1234"),
            lastModifiedBy = "some user",
            lastModifiedAt = Instant.parse("2024-01-17T14:21:17Z")
          )
        ),
        ScenarioActivity.ScenarioCanceled(
          id = UUID.fromString("c354eba1-de97-455c-b977-74729c41ce7"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          comment = ScenarioActivityComment(
            comment = Some("Canceled because marketing campaign ended"),
            lastModifiedBy = "some user",
            lastModifiedAt = Instant.parse("2024-01-17T14:21:17Z")
          )
        ),
        ScenarioActivity.ScenarioModified(
          id = UUID.fromString("07b04d45-c7c0-4980-a3bc-3c7f66410f68"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          comment = ScenarioActivityComment(
            comment = Some("Added new processing step"),
            lastModifiedBy = "some user",
            lastModifiedAt = Instant.parse("2024-01-17T14:21:17Z")
          )
        ),
        ScenarioActivity.ScenarioNameChanged(
          id = UUID.fromString("da3d1f78-7d73-4ed9-b0e5-95538e150d0d"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          oldName = "marketing campaign",
          newName = "old marketing campaign",
        ),
        ScenarioActivity.CommentAdded(
          id = UUID.fromString("edf8b047-9165-445d-a173-ba61812dbd63"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          comment = ScenarioActivityComment(
            comment = Some("Added new processing step"),
            lastModifiedBy = "some user",
            lastModifiedAt = Instant.parse("2024-01-17T14:21:17Z")
          )
        ),
        ScenarioActivity.CommentAdded(
          id = UUID.fromString("369367d6-d445-4327-ac23-4a94367b1d9e"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          comment = ScenarioActivityComment(
            comment = None,
            lastModifiedBy = "John Doe",
            lastModifiedAt = Instant.parse("2024-01-18T14:21:17Z")
          )
        ),
        ScenarioActivity.AttachmentAdded(
          id = UUID.fromString("b29916a9-34d4-4fc2-a6ab-79569f68c0b2"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          attachment = ScenarioActivityAttachment(
            id = Some(10000001),
            filename = "attachment01.png",
            lastModifiedBy = "some user",
            lastModifiedAt = Instant.parse("2024-01-17T14:21:17Z")
          ),
        ),
        ScenarioActivity.AttachmentAdded(
          id = UUID.fromString("d0a7f4a2-abcc-4ffa-b1ca-68f6da3e999a"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          attachment = ScenarioActivityAttachment(
            id = None,
            filename = "attachment01.png",
            lastModifiedBy = "John Doe",
            lastModifiedAt = Instant.parse("2024-01-18T14:21:17Z")
          ),
        ),
        ScenarioActivity.ChangedProcessingMode(
          id = UUID.fromString("683df470-0b33-4ead-bf61-fa35c63484f3"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          from = "Request-Response",
          to = "Batch",
        ),
        ScenarioActivity.IncomingMigration(
          id = UUID.fromString("4da0f1ac-034a-49b6-81c9-8ee48ba1d830"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          sourceEnvironment = "preprod",
          sourceScenarioVersion = "23",
        ),
        ScenarioActivity.OutgoingMigration(
          id = UUID.fromString("49fcd45d-3fa6-48d4-b8ed-b3055910c7ad"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          comment = ScenarioActivityComment(
            comment = Some("Added new processing step"),
            lastModifiedBy = "some user",
            lastModifiedAt = Instant.parse("2024-01-17T14:21:17Z")
          ),
          destinationEnvironment = "preprod",
        ),
        ScenarioActivity.PerformedSingleExecution(
          id = UUID.fromString("924dfcd3-fbc7-44ea-8763-813874382204"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          dateFinished = Instant.parse("2024-01-17T14:21:17Z"),
          errorMessage = Some("Execution error occurred"),
        ),
        ScenarioActivity.PerformedSingleExecution(
          id = UUID.fromString("924dfcd3-fbc7-44ea-8763-813874382204"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          dateFinished = Instant.parse("2024-01-17T14:21:17Z"),
          errorMessage = None,
        ),
        ScenarioActivity.PerformedScheduledExecution(
          id = UUID.fromString("9b27797e-aa03-42ba-8406-d0ae8005a883"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          dateFinished = Instant.parse("2024-01-17T14:21:17Z"),
          errorMessage = None,
        ),
        ScenarioActivity.AutomaticUpdate(
          id = UUID.fromString("33509d37-7657-4229-940f-b5736c82fb13"),
          user = "some user",
          date = Instant.parse("2024-01-17T14:21:17Z"),
          scenarioVersion = Some(1),
          dateFinished = Instant.parse("2024-01-17T14:21:17Z"),
          changes = "JIRA-12345, JIRA-32146",
          errorMessage = None,
        ),
      ),
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
    value = NoComment("a76d6eba-9b6c-4d97-aaa1-984a23f88019")
  )

}

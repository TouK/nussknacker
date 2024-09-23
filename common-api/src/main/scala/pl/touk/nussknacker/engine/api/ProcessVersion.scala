package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}

// We should split this class - see TODO in ScenarioAction
case class ProcessVersion(
    versionId: VersionId,
    processName: ProcessName,
    processId: ProcessId,
    labels: List[String],
    user: String,
    modelVersion: Option[Int]
)

object ProcessVersion {

  // only for testing etc.
  val empty: ProcessVersion = ProcessVersion(
    versionId = VersionId.initialVersionId,
    processName = ProcessName(""),
    processId = ProcessId(1),
    labels = List.empty,
    user = "",
    modelVersion = None
  )

  implicit val encoder: Encoder[ProcessVersion] = io.circe.generic.semiauto.deriveEncoder

  implicit val decoder: Decoder[ProcessVersion] = {
    Decoder.instance { c =>
      for {
        versionId    <- c.downField("versionId").as[VersionId]
        processName  <- c.downField("processName").as[ProcessName]
        processId    <- c.downField("processId").as[ProcessId]
        labels       <- c.downField("labels").as[Option[List[String]]]
        user         <- c.downField("user").as[String]
        modelVersion <- c.downField("modelVersion").as[Option[Int]]
      } yield ProcessVersion(
        versionId,
        processName,
        processId,
        labels.getOrElse(List.empty),
        user,
        modelVersion
      )
    }
  }

}

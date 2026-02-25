package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationErrors, ValidationWarnings}

package object process {

  object UpdateScenarioNameResponse {
    def create(oldNameString: ProcessName, newNameString: ProcessName): UpdateScenarioNameResponse =
      UpdateScenarioNameResponse(oldNameString, newNameString)
  }

  @JsonCodec(encodeOnly = true) final case class UpdateScenarioNameResponse(oldName: ProcessName, newName: ProcessName)

  @JsonCodec(encodeOnly = true) final case class CreateScenarioResponse(
      id: ProcessId,
      versionId: VersionId,
      processName: ProcessName
  )

  // This class is backward-compatible with ValidationResult which was returned before the change during put operation
  @JsonCodec final case class UpdateScenarioResponse(
      errors: ValidationErrors,
      warnings: ValidationWarnings,
      nodeResults: Map[String, NodeTypingData],
      nodeNames: Map[String, String] = Map.empty,
      newVersion: Option[VersionId]
  )

}

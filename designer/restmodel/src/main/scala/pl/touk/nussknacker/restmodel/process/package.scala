package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

package object process {

  @JsonCodec final case class UpdateProcessCategoryResponse(oldCategory: String, newCategory: String)

  object UpdateProcessNameResponse {
    def create(oldNameString: ProcessName, newNameString: ProcessName): UpdateProcessNameResponse =
      UpdateProcessNameResponse(oldNameString, newNameString)
  }

  @JsonCodec final case class UpdateProcessNameResponse(oldName: ProcessName, newName: ProcessName)

  @JsonCodec final case class ProcessResponse(id: ProcessId, versionId: VersionId, processName: ProcessName)

  @JsonCodec final case class UpdateProcessResponse(
      processResponse: Option[ProcessResponse],
      validationResult: ValidationResult
  )

}

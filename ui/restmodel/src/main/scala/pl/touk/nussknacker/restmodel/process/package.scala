package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

package object process {

  @JsonCodec final case class UpdateProcessCategoryResponse(oldCategory: String, newCategory: String)

  object UpdateProcessNameResponse {
    def create(oldNameString: String, newNameString: String): UpdateProcessNameResponse =
      UpdateProcessNameResponse(ProcessName(oldNameString), ProcessName(newNameString))
  }

  @JsonCodec final case class UpdateProcessNameResponse(oldName: ProcessName, newName: ProcessName)

  @JsonCodec final case class ProcessResponse(id: ProcessId, versionId: ProcessVersionId, processName: ProcessName)

  @JsonCodec final case class UpdateProcessResponse(processResponse: Option[ProcessResponse], validationResult: ValidationResult)

}

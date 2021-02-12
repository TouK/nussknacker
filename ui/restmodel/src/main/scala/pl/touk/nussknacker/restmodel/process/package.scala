package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

package object process {

  @JsonCodec case class UpdateProcessCategoryResponse(oldCategory: String, newCategory: String)

  object UpdateProcessNameResponse {
    def create(oldNameString: String, newNameString: String): UpdateProcessNameResponse =
      UpdateProcessNameResponse(ProcessName(oldNameString), ProcessName(newNameString))
  }

  @JsonCodec case class UpdateProcessNameResponse(oldName: ProcessName, newName: ProcessName)

  @JsonCodec case class ProcessResponse(id: ProcessId, versionId: ProcessVersionId, processName: ProcessName, json: Option[String], createDate: Long, user: String)

  @JsonCodec case class UpdateProcessResponse(processResponse: Option[ProcessResponse], validationResult: ValidationResult)

}

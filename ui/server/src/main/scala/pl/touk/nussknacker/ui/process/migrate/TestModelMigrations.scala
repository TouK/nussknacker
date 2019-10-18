package pl.touk.nussknacker.ui.process.migrate

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.restmodel.processdetails.ValidatedProcessDetails
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, ValidationErrors, ValidationResult, ValidationWarnings}

class TestModelMigrations(migrations: Map[ProcessingType, ProcessMigrations], processValidation: ProcessValidation) {

  def testMigrations(processes: List[ValidatedProcessDetails], subprocesses: List[ValidatedProcessDetails]) : List[TestMigrationResult] = {
    val migratedSubprocesses = subprocesses.flatMap(migrateProcess)
    val migratedProcesses = processes.flatMap(migrateProcess)
    val validation = processValidation.withSubprocessResolver(new SubprocessResolver(prepareSubprocessRepository(migratedSubprocesses.map(s => (s.newProcess, s.processCategory)))))
    (migratedSubprocesses ++ migratedProcesses).map { migrationDetails =>
      val validationResult = validation.validate(migrationDetails.newProcess)
      val newErrors = extractNewErrors(migrationDetails.oldProcessErrors, validationResult)
      TestMigrationResult(new ValidatedDisplayableProcess(migrationDetails.newProcess, validationResult), newErrors, migrationDetails.shouldFail)
    }
  }

  private def migrateProcess(process: ValidatedProcessDetails) : Option[MigratedProcessDetails] = {
    val migrator = new ProcessModelMigrator(migrations)
    for {
      previousResult <- process.json.map(_.validationResult)
      MigrationResult(newProcess, migrations) <- migrator.migrateProcess(process.mapProcess(_.toDisplayable))
      displayable = ProcessConverter.toDisplayable(newProcess, process.processingType)
    } yield {
      MigratedProcessDetails(displayable, previousResult, migrations.exists(_.failOnNewValidationError), process.processCategory)
    }
  }

  private def prepareSubprocessRepository(subprocesses: List[(DisplayableProcess, String)]) = {
    val subprocessesDetails = subprocesses.map { case (displayable, category) =>
      val canonical = ProcessConverter.fromDisplayable(displayable)
      SubprocessDetails(canonical, category)
    }
    new SubprocessRepository {
      override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] = {
        subprocessesDetails.toSet
      }
    }
  }

  private def extractNewErrors(before: ValidationResult, after: ValidationResult) : ValidationResult = {

    def diffOnMap(before: Map[String, List[NodeValidationError]], after: Map[String, List[NodeValidationError]]) = {
      after.map {
        case (nodeId, errors) => (nodeId, errors.diff(before.getOrElse(nodeId, List())))
      }.filterNot(_._2.isEmpty)
    }

    ValidationResult(
      ValidationErrors(
        diffOnMap(before.errors.invalidNodes, after.errors.invalidNodes),
        after.errors.processPropertiesErrors.diff(before.errors.processPropertiesErrors),
        after.errors.globalErrors.diff(before.errors.globalErrors)
      ),
      ValidationWarnings(diffOnMap(before.warnings.invalidNodes, after.warnings.invalidNodes)), Map()
    )
  }


}

@JsonCodec case class TestMigrationResult(converted: ValidatedDisplayableProcess, newErrors: ValidationResult, shouldFailOnNewErrors: Boolean) {
  def shouldFail: Boolean = {
    shouldFailOnNewErrors && !newErrors.isOk
  }
}
private case class MigratedProcessDetails(newProcess: DisplayableProcess, oldProcessErrors: ValidationResult, shouldFail: Boolean, processCategory: String)


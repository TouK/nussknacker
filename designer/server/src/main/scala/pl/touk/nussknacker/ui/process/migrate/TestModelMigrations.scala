package pl.touk.nussknacker.ui.process.migrate

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType, VersionId}
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.restmodel.validation.ValidatedDisplayableProcess
import pl.touk.nussknacker.ui.process.fragment.{FragmentDetails, FragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.validation.UIProcessValidator
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  ValidationErrors,
  ValidationResult,
  ValidationWarnings
}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

class TestModelMigrations(
    migrations: ProcessingTypeDataProvider[ProcessMigrations, _],
    processValidator: ProcessingTypeDataProvider[UIProcessValidator, _]
) {

  def testMigrations(
      processes: List[ScenarioWithDetails],
      fragments: List[ScenarioWithDetails]
  )(implicit user: LoggedUser): List[TestMigrationResult] = {
    val migratedFragments = fragments.flatMap(migrateProcess)
    val migratedProcesses = processes.flatMap(migrateProcess)
    val validator = processValidator.mapValues(
      _.withFragmentResolver(
        new FragmentResolver(prepareFragmentRepository(migratedFragments.map(s => (s.newProcess, s.processCategory))))
      )
    )
    (migratedFragments ++ migratedProcesses).map { migrationDetails =>
      val validationResult =
        validator.forTypeUnsafe(migrationDetails.newProcess.processingType).validate(migrationDetails.newProcess)
      val newErrors = extractNewErrors(migrationDetails.oldProcessErrors, validationResult)
      TestMigrationResult(
        ValidatedDisplayableProcess.withValidationResult(migrationDetails.newProcess, validationResult),
        newErrors,
        migrationDetails.shouldFail
      )
    }
  }

  private def migrateProcess(
      process: ScenarioWithDetails
  )(implicit user: LoggedUser): Option[MigratedProcessDetails] = {
    val migrator = new ProcessModelMigrator(migrations)
    for {
      MigrationResult(newProcess, migrations) <- migrator.migrateProcess(
        process.toEntityWithScenarioGraphUnsafe,
        skipEmptyMigrations = false
      )
      displayable = ProcessConverter.toDisplayable(newProcess, process.processingType, process.processCategory)
    } yield {
      MigratedProcessDetails(
        displayable,
        process.validationResultUnsafe,
        migrations.exists(_.failOnNewValidationError),
        process.processCategory
      )
    }
  }

  private def prepareFragmentRepository(fragments: List[(DisplayableProcess, String)]) = {
    val fragmentsDetails = fragments.map { case (displayable, category) =>
      val canonical = ProcessConverter.fromDisplayable(displayable)
      displayable.processingType -> FragmentDetails(canonical, category)
    }.toGroupedMap
    new FragmentRepository {
      override def fetchLatestFragments(processingType: ProcessingType)(
          implicit user: LoggedUser
      ): Future[List[FragmentDetails]] =
        Future.successful(fragmentsDetails.getOrElse(processingType, List.empty))
      override def fetchLatestFragment(processName: ProcessName)(
          implicit user: LoggedUser
      ): Future[Option[FragmentDetails]] =
        throw new IllegalStateException("FragmentRepository.get(ProcessName) used during migration")
    }
  }

  private def extractNewErrors(before: ValidationResult, after: ValidationResult): ValidationResult = {
    // simplified comparison key: we ignore error message and description
    def errorToKey(error: NodeValidationError) = (error.fieldName, error.errorType, error.typ)

    def diffErrorLists(before: List[NodeValidationError], after: List[NodeValidationError]) = {
      val errorsBefore = before.map(errorToKey).toSet
      after.filterNot(error => errorsBefore.contains(errorToKey(error)))
    }

    def diffOnMap(before: Map[String, List[NodeValidationError]], after: Map[String, List[NodeValidationError]]) = {
      after
        .map { case (nodeId, errorsAfter) =>
          (nodeId, diffErrorLists(before.getOrElse(nodeId, List.empty), errorsAfter))
        }
        .filterNot(_._2.isEmpty)
    }

    ValidationResult(
      ValidationErrors(
        diffOnMap(before.errors.invalidNodes, after.errors.invalidNodes),
        diffErrorLists(before.errors.processPropertiesErrors, after.errors.processPropertiesErrors),
        diffErrorLists(before.errors.globalErrors, after.errors.globalErrors)
      ),
      ValidationWarnings(diffOnMap(before.warnings.invalidNodes, after.warnings.invalidNodes)),
      Map.empty
    )
  }

}

@JsonCodec final case class TestMigrationResult(
    converted: ValidatedDisplayableProcess,
    newErrors: ValidationResult,
    shouldFailOnNewErrors: Boolean
) {

  def shouldFail: Boolean = {
    shouldFailOnNewErrors && (newErrors.hasErrors || newErrors.hasWarnings)
  }

}

private final case class MigratedProcessDetails(
    newProcess: DisplayableProcess,
    oldProcessErrors: ValidationResult,
    shouldFail: Boolean,
    processCategory: String
)

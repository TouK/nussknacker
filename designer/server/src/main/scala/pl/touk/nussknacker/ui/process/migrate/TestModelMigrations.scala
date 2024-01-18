package pl.touk.nussknacker.ui.process.migrate

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.migration.ProcessMigrations
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

import scala.collection.parallel.ExecutionContextTaskSupport
import scala.collection.parallel.immutable.ParVector
import scala.concurrent.ExecutionContext

class TestModelMigrations(
    migrations: ProcessingTypeDataProvider[ProcessMigrations, _],
    processValidator: ProcessingTypeDataProvider[UIProcessValidator, _]
) extends LazyLogging {

  def testMigrations(
      processes: List[ScenarioWithDetails],
      fragments: List[ScenarioWithDetails],
      batchingExecutionContext: ExecutionContext
  )(implicit user: LoggedUser): List[TestMigrationResult] = {
    logger.debug(
      s"Testing scenario migrations (scenarios=${processes.count(_ => true)}, fragments=${fragments.count(_ => true)})"
    )
    val migratedFragments = fragments.flatMap(migrateProcess)
    val migratedProcesses = processes.flatMap(migrateProcess)
    logger.debug("Validating migrated scenarios")
    val validator = processValidator.mapValues(
      _.withFragmentResolver(
        new FragmentResolver(prepareFragmentRepository(migratedFragments.map(s => (s.newProcess, s.processCategory))))
      )
    )
    processInParallel(migratedFragments ++ migratedProcesses, batchingExecutionContext) { migrationDetails =>
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
      FragmentDetails(canonical, category)
    }
    new FragmentRepository {
      override def loadFragments(versions: Map[String, VersionId]): Set[FragmentDetails] =
        fragmentsDetails.toSet

      override def loadFragments(versions: Map[String, VersionId], category: Category): Set[FragmentDetails] =
        loadFragments(versions).filter(_.category == category)
    }
  }

  private def processInParallel(input: List[MigratedProcessDetails], batchingExecutionContext: ExecutionContext)(
      process: MigratedProcessDetails => TestMigrationResult
  ): List[TestMigrationResult] = {
    // We create ParVector manually instead of calling par for compatibility with Scala 2.12
    val parallelCollection = new ParVector(input.toVector)
    parallelCollection.tasksupport = new ExecutionContextTaskSupport(batchingExecutionContext)
    parallelCollection.map(process).toList
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

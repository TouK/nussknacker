package pl.touk.nussknacker.ui.process.migrate

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  ValidationErrors,
  ValidationResult,
  ValidationWarnings
}
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions._
import pl.touk.nussknacker.ui.process.fragment.{FragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator

import scala.collection.parallel.ExecutionContextTaskSupport
import scala.collection.parallel.immutable.ParVector
import scala.concurrent.{ExecutionContext, Future}

class TestModelMigrations(
    migrators: ProcessingTypeDataProvider[ProcessModelMigrator, _],
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
        new FragmentResolver(prepareFragmentRepository(migratedFragments))
      )
    )
    processInParallel(migratedFragments ++ migratedProcesses, batchingExecutionContext) { migrationDetails =>
      val validationResult =
        validator
          .forTypeUnsafe(migrationDetails.processingType)
          .validate(migrationDetails.newProcess, migrationDetails.processName, migrationDetails.isFragment)
      val newErrors = extractNewErrors(migrationDetails.oldProcessErrors, validationResult)
      TestMigrationResult(
        migrationDetails.processName,
        newErrors
      )
    }
  }

  private def migrateProcess(
      process: ScenarioWithDetails
  )(implicit user: LoggedUser): Option[MigratedProcessDetails] = {
    for {
      migrator <- migrators.forType(process.processingType)
      MigrationResult(newProcess, _) <- migrator.migrateProcess(
        process.toEntityWithScenarioGraphUnsafe,
        skipEmptyMigrations = false
      )
      displayable = ProcessConverter.toDisplayable(newProcess)
    } yield {
      MigratedProcessDetails(
        process.name,
        process.processingType,
        process.isFragment,
        displayable,
        process.validationResultUnsafe
      )
    }
  }

  private def prepareFragmentRepository(fragments: List[MigratedProcessDetails]) = {
    val fragmentsByProcessingType = fragments.map { fragmentDetails =>
      val canonical = ProcessConverter.fromDisplayable(fragmentDetails.newProcess, fragmentDetails.processName)
      fragmentDetails.processingType -> canonical
    }.toGroupedMap
    new FragmentRepository {

      override def fetchLatestFragments(processingType: ProcessingType)(
          implicit user: LoggedUser
      ): Future[List[CanonicalProcess]] =
        Future.successful(fragmentsByProcessingType.getOrElse(processingType, List.empty))

      override def fetchLatestFragment(fragmentName: ProcessName)(
          implicit user: LoggedUser
      ): Future[Option[CanonicalProcess]] =
        throw new IllegalStateException("FragmentRepository.get(ProcessName) used during migration")
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

final case class TestMigrationResult(processName: ProcessName, newErrors: ValidationResult)

private final case class MigratedProcessDetails(
    processName: ProcessName,
    processingType: ProcessingType,
    isFragment: Boolean,
    newProcess: DisplayableProcess,
    oldProcessErrors: ValidationResult
)

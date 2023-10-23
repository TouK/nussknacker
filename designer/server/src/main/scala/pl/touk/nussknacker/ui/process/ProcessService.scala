package pl.touk.nussknacker.ui.process

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessAction, ProcessActionType, ProcessState}
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.FragmentInput
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process._
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  ValidationResult
}
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.process.ProcessService.{CreateProcessCommand, EmptyResponse, UpdateProcessCommand}
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.exception.{ProcessIllegalAction, ProcessValidationError}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{
  CreateProcessAction,
  ProcessCreated,
  RemoteUserName,
  UpdateProcessAction
}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.{FatalValidationError, ProcessValidation}
import slick.dbio.DBIOAction

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object ProcessService {
  type EmptyResponse = XError[Unit]

  @JsonCodec final case class CreateProcessCommand(
      processName: ProcessName,
      category: String,
      isFragment: Boolean,
      forwardedUserName: Option[RemoteUserName]
  )

  @JsonCodec final case class UpdateProcessCommand(
      process: DisplayableProcess,
      comment: UpdateProcessComment,
      forwardedUserName: Option[RemoteUserName]
  )

}

trait ProcessService {

  def getProcess[PS: ProcessShapeFetchStrategy](processIdWithName: ProcessIdWithName)(
      implicit user: LoggedUser
  ): Future[XError[BaseProcessDetails[PS]]]

  def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def renameProcess(processIdWithName: ProcessIdWithName, name: ProcessName)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessNameResponse]]

  def updateCategory(processIdWithName: ProcessIdWithName, category: String)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessCategoryResponse]]

  def createProcess(command: CreateProcessCommand)(implicit user: LoggedUser): Future[XError[ProcessResponse]]

  def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessResponse]]

  def getProcesses[PS: ProcessShapeFetchStrategy](implicit user: LoggedUser): Future[List[BaseProcessDetails[PS]]]

  def getProcessesAndFragments[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]]

  def getArchivedProcessesAndFragments[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]]

  def getFragmentsDetails(processingTypes: Option[List[ProcessingType]])(
      implicit user: LoggedUser
  ): Future[Set[FragmentDetails]]

  def importProcess(processId: ProcessIdWithName, processData: String)(
      implicit user: LoggedUser
  ): Future[XError[ValidatedDisplayableProcess]]

  def getProcessActions(id: ProcessId): Future[List[ProcessAction]]
}

/**
 * ProcessService provides functionality for archive, unarchive, deploy, cancel process.
 * Each action includes verification based on actual process state and checking process is fragment / archived.
 */
class DBProcessService(
    deploymentService: DeploymentService,
    newProcessPreparer: NewProcessPreparer,
    processCategoryService: ProcessCategoryService,
    processResolving: UIProcessResolving,
    dbioRunner: DBIOActionRunner,
    fetchingProcessRepository: FetchingProcessRepository[Future],
    processActionRepository: ProcessActionRepository[DB],
    processRepository: ProcessRepository[DB],
    processValidation: ProcessValidation,
    fixedValuesPresetProvider: FixedValuesPresetProvider
)(implicit ec: ExecutionContext)
    extends ProcessService
    with LazyLogging {

  import cats.instances.future._
  import cats.syntax.either._

  override def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
    withNotArchivedProcess(processIdWithName, ProcessActionType.Archive) { process =>
      if (process.isFragment) {
        doArchive(process)
      } else {
        // FIXME: This doesn't work correctly because concurrent request can change a state and double archive actions will be done.
        //        See ManagementResourcesConcurrentSpec and how DeploymentService handles it correctly for deploy and cancel
        doOnProcessStateVerification(process, ProcessActionType.Archive)(doArchive(process))
      }
    }

  override def renameProcess(processIdWithName: ProcessIdWithName, name: ProcessName)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessNameResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't rename archived scenario.") { process =>
      if (process.isFragment) {
        doRename(processIdWithName, name)
      } else {
        doOnProcessStateVerification(process, ProcessActionType.Rename)(doRename(processIdWithName, name))
      }
    }

  override def unArchiveProcess(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser): Future[EmptyResponse] =
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        dbioRunner
          .runInTransaction(
            DBIOAction.seq(
              processRepository.archive(processId = process.idWithName.id, isArchived = false),
              processActionRepository
                .markProcessAsUnArchived(processId = process.idWithName.id, process.processVersionId)
            )
          )
          .map(_ => ().asRight)
      } else {
        Future(Left(ProcessIllegalAction("Can't unarchive not archived scenario.")))
      }
    }

  override def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
    withArchivedProcess(processIdWithName, "Can't delete not archived scenario.") { process =>
      dbioRunner
        .runInTransaction(
          processRepository.deleteProcess(processIdWithName.id)
        )
        .map(_ => ().asRight)
    }

  override def updateCategory(processIdWithName: ProcessIdWithName, category: String)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessCategoryResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't update category archived scenario.") { process =>
      withProcessingType(category) { _ =>
        dbioRunner.runInTransaction(
          processRepository
            .updateCategory(processIdWithName.id, category)
            .map {
              case Right(_)    => Right(UpdateProcessCategoryResponse(process.processCategory, category))
              case Left(value) => Left(value)
            }
        )
      }
    }

  // FIXME: Create process should create two inserts (process, processVersion) in transactional way, but right we do all in process repository..
  override def createProcess(
      command: CreateProcessCommand
  )(implicit user: LoggedUser): Future[XError[ProcessResponse]] =
    withProcessingType(command.category) { processingType =>
      val emptyCanonicalProcess =
        newProcessPreparer.prepareEmptyProcess(command.processName.value, processingType, command.isFragment)
      val action = CreateProcessAction(
        command.processName,
        command.category,
        emptyCanonicalProcess,
        processingType,
        command.isFragment,
        command.forwardedUserName
      )

      val propertiesErrors = validateInitialScenarioProperties(emptyCanonicalProcess, processingType, command.category)

      if (propertiesErrors.nonEmpty) {
        Future.successful(Left(ProcessValidationError(propertiesErrors.map(_.message).mkString(", "))))
      } else {
        dbioRunner
          .runInTransaction(processRepository.saveNewProcess(action))
          .map {
            case Right(maybemaybeCreated) =>
              maybemaybeCreated
                .map(created => Right(toProcessResponse(command.processName, created)))
                .getOrElse(Left(ProcessValidationError("Unknown error on creating scenario.")))
            case Left(value) =>
              Left(value)
          }
      }
    }

  // FIXME: Update process should update process and create process version in transactional way, but right we do all in process repository..
  override def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't update graph archived scenario.") { _ =>
      val (processWithFixedValuePresets, presetValidation) =
        substituteFixedValuesPresets(action.process, fixedValuesPresetProvider)

      val result = for {
        validation <- EitherT.fromEither[Future](
          FatalValidationError.saveNotAllowedAsError(
            presetValidation.add(processResolving.validateBeforeUiResolving(processWithFixedValuePresets))
          )
        )
        substituted = {
          processResolving.resolveExpressions(processWithFixedValuePresets, validation.typingInfo)
        }
        updateProcessAction = UpdateProcessAction(
          processIdWithName.id,
          substituted,
          Option(action.comment),
          increaseVersionWhenJsonNotChanged = false,
          forwardedUserName = action.forwardedUserName
        )
        processUpdated <- EitherT(dbioRunner.runInTransaction(processRepository.updateProcess(updateProcessAction)))
      } yield UpdateProcessResponse(
        processUpdated.newVersion
          .map(ProcessCreated(processIdWithName.id, _))
          .map(toProcessResponse(processIdWithName.name, _)),
        validation
      )

      result.value
    }

  override def getProcess[PS: ProcessShapeFetchStrategy](
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser): Future[XError[BaseProcessDetails[PS]]] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[PS](processIdWithName.id).flatMap {
      case Some(process) =>
        Future(Right(process))
      case None =>
        Future(Left(ProcessNotFoundError(processIdWithName.id.value.toString)))
    }

  override def getProcesses[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]] =
    getProcesses(user, isFragment = Some(false), isArchived = Some(false))

  override def getProcessesAndFragments[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]] =
    getProcesses(user, isFragment = None, isArchived = Some(false))

  override def getArchivedProcessesAndFragments[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]] =
    getProcesses(user, isFragment = None, isArchived = Some(true))

  // TODO: It's temporary solution to return Set[FragmentDetails], in future we should replace it by Set[BaseProcessDetails[PS]]
  override def getFragmentsDetails(
      processingTypes: Option[List[ProcessingType]]
  )(implicit user: LoggedUser): Future[Set[FragmentDetails]] = {
    fetchingProcessRepository
      .fetchProcessesDetails[CanonicalProcess](
        FetchProcessesDetailsQuery(isFragment = Some(true), isArchived = Some(false), processingTypes = processingTypes)
      )
      .map(processes =>
        processes
          .map(sub => {
            FragmentDetails(sub.json, sub.processCategory)
          })
          .toSet
      )
  }

  def importProcess(processId: ProcessIdWithName, jsonString: String)(
      implicit user: LoggedUser
  ): Future[XError[ValidatedDisplayableProcess]] = {
    withNotArchivedProcess(processId, "Import is not allowed for archived process.") { process =>
      val result = ProcessMarshaller
        .fromJson(jsonString)
        .leftMap(UnmarshallError)
        .toEither
        .map { jsonCanonicalProcess =>
          val canonical   = jsonCanonicalProcess.withProcessId(processId.name)
          val displayable = ProcessConverter.toDisplayable(canonical, process.processingType, process.processCategory)
          val validationResult = processResolving.validateBeforeUiReverseResolving(
            canonical,
            displayable.processingType,
            process.processCategory
          )
          new ValidatedDisplayableProcess(displayable, validationResult)
        }

      Future.successful(result)
    }
  }

  private def substituteFixedValuesPresets(
      process: DisplayableProcess,
      fixedValuesPresetProvider: FixedValuesPresetProvider
  ): (DisplayableProcess, ValidationResult) = {
    val fixedValuePresets =
      try {
        fixedValuesPresetProvider.getAll
      } catch {
        case e: Throwable =>
          logger.warn(s"FixedValuePresetsProvider failed to provide presets ", e)
          return (
            process,
            ValidationResult.globalErrors(
              List(
                NodeValidationError(
                  "FixedValuePresetsProviderFailure",
                  "FixedValuePresetsProvider failed to provide presets",
                  "FixedValuePresetsProvider failed to provide presets",
                  None,
                  NodeValidationErrorType.SaveAllowed
                )
              )
            )
          )
      }

    val nodeErrors = mutable.Map[String, List[NodeValidationError]]()
    val substituted = process.copy(
      nodes = process.nodes.map {
        case fragmentInput: FragmentInput =>
          fragmentInput.copy(
            fragmentParams = fragmentInput.fragmentParams.map(_.map { param =>
              param.fixedValueListPresetId match {
                case Some(presetId) =>
                  fixedValuePresets.get(presetId) match {
                    case Some(preset) =>
                      param.copy(fixedValueList =
                        preset.map(v => node.FragmentInputDefinition.FixedExpressionValue(v.expression, v.label))
                      )
                    case None =>
                      nodeErrors(fragmentInput.id) = NodeValidationError(
                        "FixedValuePresetNotFound",
                        s"Preset with id='$presetId' not found",
                        s"Preset with id='$presetId' not found",
                        Some(param.name),
                        NodeValidationErrorType.SaveAllowed
                      ) :: nodeErrors.getOrElse(fragmentInput.id, List[NodeValidationError]())

                      param.copy(fixedValueList =
                        List.empty
                      ) // clear fixedValueList instead of possibly using outdated presets
                  }
                case None => param
              }

            })
          )
        case node => node
      }
    )

    (substituted, ValidationResult.errors(nodeErrors.toMap, List.empty, List.empty))
  }

  private def validateInitialScenarioProperties(
      canonicalProcess: CanonicalProcess,
      processingType: ProcessingType,
      category: String
  ) = {
    val validationResult =
      processValidation.processingTypeValidationWithTypingInfo(canonicalProcess, processingType, category)
    validationResult.errors.processPropertiesErrors

  }

  private def doOnProcessStateVerification[T](process: BaseProcessDetails[_], actionToCheck: ProcessActionType)(
      callback: => Future[XError[T]]
  )(implicit user: LoggedUser): Future[XError[T]] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    deploymentService
      .getProcessState(process)
      .flatMap(state => {
        if (state.allowedActions.contains(actionToCheck)) {
          callback
        } else {
          Future(Left(ProcessIllegalAction(actionToCheck, process.idWithName, state)))
        }
      })
  }

  private def doArchive(process: BaseProcessDetails[_])(implicit user: LoggedUser): Future[EmptyResponse] =
    dbioRunner
      .runInTransaction(
        DBIOAction.seq(
          processRepository.archive(processId = process.idWithName.id, isArchived = true),
          processActionRepository.markProcessAsArchived(processId = process.idWithName.id, process.processVersionId)
        )
      )
      .map(_ => ().asRight)

  private def doRename(processIdWithName: ProcessIdWithName, name: ProcessName)(implicit user: LoggedUser) = {
    dbioRunner.runInTransaction(
      processRepository
        .renameProcess(processIdWithName, name)
        .map {
          case Right(_)    => Right(UpdateProcessNameResponse.create(processIdWithName.name.value, name.value))
          case Left(value) => Left(value)
        }
    )
  }

  override def getProcessActions(id: ProcessId): Future[List[ProcessAction]] = {
    dbioRunner.runInTransaction(processActionRepository.getFinishedProcessActions(id, None))
  }

  private def toProcessResponse(processName: ProcessName, created: ProcessCreated): ProcessResponse =
    ProcessResponse(created.processId, created.processVersionId, processName)

  private def withProcess[T](
      processIdWithName: ProcessIdWithName
  )(callback: BaseProcessDetails[_] => Future[XError[T]])(implicit user: LoggedUser) = {
    getProcess[Unit](processIdWithName).flatMap {
      case Left(err) => Future(Left(err))
      case Right(t)  => callback(t)
    }
  }

  private def withArchivedProcess[T](processIdWithName: ProcessIdWithName, errorMessage: String)(
      callback: BaseProcessDetails[_] => Future[XError[T]]
  )(implicit user: LoggedUser): Future[XError[T]] = {
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        callback(process)
      } else {
        Future(Left(ProcessIllegalAction(errorMessage)))
      }
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, errorMessage: String)(
      callback: BaseProcessDetails[_] => Future[XError[T]]
  )(implicit user: LoggedUser): Future[XError[T]] = {
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        Future(Left(ProcessIllegalAction(errorMessage)))
      } else {
        callback(process)
      }
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, action: ProcessActionType)(
      callback: BaseProcessDetails[_] => Future[XError[T]]
  )(implicit user: LoggedUser): Future[XError[T]] =
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        Future(Left(ProcessIllegalAction.archived(action, process.idWithName)))
      } else {
        callback(process)
      }
    }

  private def withProcessingType[T](
      category: String
  )(callback: ProcessingType => Future[Either[EspError, T]]): Future[Either[EspError, T]] =
    processCategoryService.getTypeForCategory(category) match {
      case Some(processingType) =>
        callback(processingType)
      case None =>
        Future(Left(ProcessValidationError("Scenario category not found.")))
    }

  private def getProcesses[PS: ProcessShapeFetchStrategy](
      user: LoggedUser,
      isFragment: Option[Boolean],
      isArchived: Option[Boolean]
  ): Future[List[BaseProcessDetails[PS]]] = {
    val userCategories = processCategoryService.getUserCategories(user)
    val shapeStrategy  = implicitly[ProcessShapeFetchStrategy[PS]]
    fetchingProcessRepository.fetchProcessesDetails(
      FetchProcessesDetailsQuery(isFragment = isFragment, isArchived = isArchived, categories = Some(userCategories))
    )(shapeStrategy, user, ec)
  }

}

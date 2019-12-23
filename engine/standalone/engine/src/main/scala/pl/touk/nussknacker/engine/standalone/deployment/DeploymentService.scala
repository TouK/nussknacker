package pl.touk.nussknacker.engine.standalone.deployment

import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState, ProcessStateCustomConfigurator, StateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{JobData, StandaloneMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.{ProcessMarshaller, ProcessUnmarshallError}
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.standalone.management.StandaloneProcessManagerProvider
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer

import scala.concurrent.ExecutionContext

object DeploymentService {

  //TODO this is temporary solution, we should keep these processes e.g. in ZK
  //also: how to pass model data around?
  def apply(context: StandaloneContextPreparer, config: Config): DeploymentService = {
    val modelData = StandaloneProcessManagerProvider.defaultTypeConfig(config).toModelData
    new DeploymentService(context, modelData, FileProcessRepository(config.getString("standaloneEngineProcessLocation")))
  }

}

class DeploymentService(context: StandaloneContextPreparer, modelData: ModelData,
                        processRepository: ProcessRepository) extends LazyLogging with ProcessInterpreters {

  private val processInterpreters: collection.concurrent.TrieMap[ProcessName, (StandaloneProcessInterpreter, DeploymentData)] = collection.concurrent.TrieMap()

  private val pathToInterpreterMap: collection.concurrent.TrieMap[String, StandaloneProcessInterpreter] = collection.concurrent.TrieMap()

  initProcesses()

  private def initProcesses() : Unit = {
    val deploymentResults = processRepository.loadAll.map { case (id, deploymentData) =>
      (id, deploy(deploymentData)(ExecutionContext.Implicits.global))
    }
    deploymentResults.collect {
      case (id, Left(errors)) => logger.error(s"Failed to deploy $id, errors: $errors")
    }
  }

  def deploy(deploymentData: DeploymentData)(implicit ec: ExecutionContext): Either[NonEmptyList[DeploymentError], Unit] = {
    val processName = deploymentData.processVersion.processName

    toEspProcess(deploymentData.processJson).andThen { process =>
      process.metaData.typeSpecificData match {
        case StandaloneMetaData(path) =>
          val pathToDeploy = path.getOrElse(processName.value)
          val currentAtPath = pathToInterpreterMap.get(pathToDeploy).map(_.id)
          currentAtPath match {
            case Some(oldId) if oldId != processName.value =>
              Invalid(NonEmptyList.of(DeploymentError(Set(), s"Process $oldId is already deployed at path $pathToDeploy")))
            case _ =>
              val interpreter = newInterpreter(process)
              interpreter.foreach { processInterpreter =>
                cancel(processName)
                processRepository.add(processName, deploymentData)
                processInterpreters.put(processName, (processInterpreter, deploymentData))
                pathToInterpreterMap.put(pathToDeploy, processInterpreter)
                processInterpreter.open(JobData(process.metaData, deploymentData.processVersion))
                logger.info(s"Successfully deployed process ${processName.value}")
              }
              interpreter.map(_ => ())
          }
        case _ => Invalid(NonEmptyList.of(DeploymentError(Set(), "Wrong process type")))
      }
    }.toEither

  }

  def checkStatus(processName: ProcessName): Option[ProcessState] = {
    processInterpreters.get(processName).map { case (_, DeploymentData(_, deploymentTime, processVersion)) => ProcessState(
        deploymentId = DeploymentId(processName.value),
        status = StateStatus.Running,
        allowedActions = ProcessStateCustomConfigurator.getStatusActions(StateStatus.Running),
        version = Option(processVersion),
        startTime = Some(deploymentTime)
      )
    }
  }

  def cancel(processName: ProcessName): Option[Unit] = {
    processRepository.remove(processName)
    val removed = processInterpreters.remove(processName)
    removed.foreach { case (interpreter, _) =>
      pathToInterpreterMap.filter(_._2 == interpreter).foreach { case (k, _) => pathToInterpreterMap.remove(k) }
    }
    removed.foreach(_._1.close())
    removed.map(_ => ())
  }

  def getInterpreterByPath(path: String): Option[StandaloneProcessInterpreter] = {
    pathToInterpreterMap.get(path)
  }

  private def newInterpreter(canonicalProcess: CanonicalProcess) =
    ProcessCanonizer.uncanonize(canonicalProcess)
      .andThen(StandaloneProcessInterpreter(_, context, modelData)).leftMap(_.map(DeploymentError(_)))


  private def toEspProcess(processJson: String): ValidatedNel[DeploymentError, CanonicalProcess] =
    ProcessMarshaller.fromJson(processJson)
      .leftMap(error => NonEmptyList.of(DeploymentError(error)))
}


case class DeploymentError(nodeIds: Set[String], message: String)

object DeploymentError {
  def apply(error: ProcessCompilationError) : DeploymentError = DeploymentError(error.nodeIds, error.toString)

  def apply(error: ProcessUnmarshallError) : DeploymentError = DeploymentError(error.nodeIds, error.toString)

}

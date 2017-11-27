package pl.touk.nussknacker.engine.standalone.deployment

import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.StandaloneMetaData
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.ProcessCompilationError
import pl.touk.nussknacker.engine.marshall.{ProcessMarshaller, ProcessUnmarshallError}
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.standalone.{StandaloneModelData, StandaloneProcessInterpreter}
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer

import scala.concurrent.ExecutionContext

object DeploymentService {

  //TODO this is temporary solution, we should keep these processes e.g. in ZK
  //also: how to pass model data around?
  def apply(context: StandaloneContextPreparer, config: Config): DeploymentService = {
    val modelData = StandaloneModelData(config)
    new DeploymentService(context, modelData, FileProcessRepository(config.getString("standaloneEngineProcessLocation")))
  }

}

class DeploymentService(context: StandaloneContextPreparer, modelData: ModelData,
                        processRepository: ProcessRepository) extends LazyLogging {

  val processInterpreters: collection.concurrent.TrieMap[String, (StandaloneProcessInterpreter, Long)] = collection.concurrent.TrieMap()

  val pathToInterpreterMap: collection.concurrent.TrieMap[String, StandaloneProcessInterpreter] = collection.concurrent.TrieMap()

  val processMarshaller = new ProcessMarshaller()

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
    val processId = deploymentData.processId

    toEspProcess(deploymentData.processJson).andThen { process =>
      process.metaData.typeSpecificData match {
        case StandaloneMetaData(path) =>
          val pathToDeploy = path.getOrElse(processId)
          val currentAtPath = pathToInterpreterMap.get(pathToDeploy).map(_.id)
          currentAtPath match {
            case Some(oldId) if oldId != processId =>
              Invalid(NonEmptyList.of(DeploymentError(Set(), s"Process $oldId is already deployed at path $pathToDeploy")))
            case _ =>
              val interpreter = newInterpreter(process)
              interpreter.foreach { processInterpreter =>
                cancel(processId)
                processRepository.add(processId, deploymentData)
                processInterpreters.put(processId, (processInterpreter, deploymentData.deploymentTime))
                pathToInterpreterMap.put(path.getOrElse(processId), processInterpreter)
                processInterpreter.open()
                logger.info(s"Successfully deployed process $processId")
              }
              interpreter.map(_ => ())
          }
        case _ => Invalid(NonEmptyList.of(DeploymentError(Set(), "Wrong process type")))
      }
    }.toEither

  }



  def checkStatus(processId: String): Option[ProcessState] = {
    processInterpreters.get(processId).map { case (_, startTime) =>
      ProcessState(processId, "RUNNING", startTime)
    }
  }

  def cancel(processId: String): Option[Unit] = {
    processRepository.remove(processId)
    val removed = processInterpreters.remove(processId)
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
    processMarshaller.fromJson(processJson)
      .leftMap(error => NonEmptyList.of(DeploymentError(error)))
}


case class DeploymentError(nodeIds: Set[String], message: String)

object DeploymentError {
  def apply(error: ProcessCompilationError) : DeploymentError = DeploymentError(error.nodeIds, error.toString)

  def apply(error: ProcessUnmarshallError) : DeploymentError = DeploymentError(error.nodeIds, error.toString)

}

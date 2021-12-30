package pl.touk.nussknacker.engine.requestresponse.deployment

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.RequestResponseMetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.{ProcessName, RunMode}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.RequestResponseEngine
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponseDeploymentData
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object DeploymentService {

  val modelConfigPath = "modelConfig"

  //TODO this is temporary solution, we should keep these processes e.g. in ZK
  //also: how to pass model data around?
  def apply(context: LiteEngineRuntimeContextPreparer, config: Config): DeploymentService = {
    val modelConfig = config.getConfig(modelConfigPath)
    val modelData = ModelData(modelConfig, ModelClassLoader(modelConfig.as[List[URL]]("classPath")))
    new DeploymentService(context, modelData, FileProcessRepository(config.getString("scenarioRepositoryLocation")))
  }

}

class DeploymentService(context: LiteEngineRuntimeContextPreparer, modelData: ModelData,
                        processRepository: ProcessRepository) extends LazyLogging with ProcessInterpreters {

  private val processInterpreters: collection.concurrent.TrieMap[ProcessName, (InterpreterType, RequestResponseDeploymentData)] = collection.concurrent.TrieMap()

  private val pathToInterpreterMap: collection.concurrent.TrieMap[String, InterpreterType] = collection.concurrent.TrieMap()

  initProcesses()

  private def initProcesses() : Unit = {
    val deploymentResults = processRepository.loadAll.map { case (id, deploymentData) =>
      (id, deploy(deploymentData)(ExecutionContext.Implicits.global))
    }
    deploymentResults.collect {
      case (id, Left(errors)) => logger.error(s"Failed to deploy $id, errors: $errors")
    }
  }

  def deploy(deploymentData: RequestResponseDeploymentData)(implicit ec: ExecutionContext): Either[NonEmptyList[DeploymentError], Unit] = {
    val processName = deploymentData.processVersion.processName

    ScenarioParser.parse(deploymentData.processJson).leftMap(_.map(DeploymentError(_))).andThen { process =>
      process.metaData.typeSpecificData match {
        case RequestResponseMetaData(path) =>
          val pathToDeploy = path.getOrElse(processName.value)
          val currentAtPath = pathToInterpreterMap.get(pathToDeploy).map(_.id)
          currentAtPath match {
            case Some(oldId) if oldId != processName.value =>
              Invalid(NonEmptyList.of(DeploymentError(Set(), s"Scenario $oldId is already deployed at path $pathToDeploy")))
            case _ =>
              val interpreter = newInterpreter(process, deploymentData)
              try {
                val validatedResult = interpreter.openValidated().map { _ =>
                  cancel(processName)
                  processRepository.add(processName, deploymentData)
                  processInterpreters.put(processName, (interpreter, deploymentData))
                  pathToInterpreterMap.put(pathToDeploy, interpreter)
                  logger.info(s"Successfully deployed scenario ${processName.value}")
                }
                validatedResult.swap.foreach(_ => interpreter.close())
                validatedResult
              } catch {
                case NonFatal(ex) =>
                  interpreter.close()
                  throw ex
              }
          }
        case _ => Invalid(NonEmptyList.of(DeploymentError(Set(), "Wrong scenario type")))
      }
    }.toEither

  }

  def checkStatus(processName: ProcessName): Option[DeploymentStatus] = {
    processInterpreters.get(processName).map { case (_, RequestResponseDeploymentData(_, deploymentTime, processVersion, _)) =>
      DeploymentStatus(processVersion, deploymentTime)
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

  def getInterpreterByPath(path: String): Option[InterpreterType] = {
    pathToInterpreterMap.get(path)
  }

  private def newInterpreter(process: EspProcess, deploymentData: RequestResponseDeploymentData): InterpreterType = {
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._

    import ExecutionContext.Implicits._

    RequestResponseEngine[Future](process, deploymentData.processVersion, deploymentData.deploymentData,
      context, modelData, Nil, ProductionServiceInvocationCollector, RunMode.Normal)
  }

}

case class DeploymentError(nodeIds: Set[String], message: String)

object DeploymentError {
  def apply(error: ProcessCompilationError) : DeploymentError = DeploymentError(error.nodeIds, error.toString)
}

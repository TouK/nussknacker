package pl.touk.nussknacker.engine.util.service.query

import java.util.UUID

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{NodeContext, QueryServiceInvocationCollector, QueryServiceResult}
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.api.{process, _}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.{DefaultServiceInvoker, ProcessDefinitionExtractor, ProcessObjectDefinitionExtractor}

import scala.concurrent.{ExecutionContext, Future}

// TODO: Processes using Flink's RuntimeContex, ex. metrics throws NPE, but in another thread, so service works.
class ServiceQuery(modelData: ModelData) {

  import ServiceQuery._


  def invoke(serviceName: String, serviceParameters: (String, Any)*)
            (implicit executionContext: ExecutionContext): Future[QueryResult] = {

    //this map has to be created for each invocation, because we close service after invocation (to avoid connection leaks etc.)
    val serviceMethodMap: Map[String, ObjectWithMethodDef] = modelData.withThisAsContextClassLoader {
      val servicesMap = modelData.configCreator.services(process.ProcessObjectDependencies(modelData.processConfig, modelData.objectNaming))
      ObjectWithMethodDef.forMap(servicesMap, ProcessObjectDefinitionExtractor.service,
        ProcessDefinitionExtractor.extractNodesConfig(modelData.processConfig))
    }
    val serviceDef: ObjectWithMethodDef = serviceMethodMap.getOrElse(serviceName, throw ServiceNotFoundException(serviceName))



    val lifecycle = closableService(serviceDef)
    lifecycle.open(jobData)
    val runId = TestRunId(UUID.randomUUID().toString)
    val collector = QueryServiceInvocationCollector(serviceName).enable(runId)
    val invocationResult = DefaultServiceInvoker(metaData, NodeId(dummyNodeContext.nodeId), None, serviceDef)
      .invokeService(serviceParameters.toMap)(executionContext, collector, ContextId(dummyNodeContext.contextId))
    val queryResult = invocationResult.map { ff =>
      QueryResult(ff, collector.getResults)
    }.recover { case ex: Exception =>
      QueryResult(s"Service query error: ${ex.getMessage}", collector.getResults)
    }
    queryResult.onComplete { _ =>
      lifecycle.close()
      collector.cleanResults()
    }
    queryResult
  }
  private def closableService(methodDef: ObjectWithMethodDef): Lifecycle = {
    methodDef.obj match {
      case lifecycle: Lifecycle => lifecycle
      case _ => throw new IllegalArgumentException
    }
  }
}

object ServiceQuery {

  case class QueryResult(result: Any, collectedResults: List[QueryServiceResult])

  private val dummyNodeContext = NodeContext(
    contextId = "dummyContextId",
    nodeId = "dummyNodeId",
    ref = "dummyRef",
    outputVariableNameOpt = None
  )
  implicit val metaData: MetaData = MetaData(
    id = "testProcess",
    typeSpecificData = StandaloneMetaData(None),
    additionalFields = None
  )
  val jobData = JobData(metaData, ProcessVersion.empty)

  case class ServiceNotFoundException(serviceName: String) extends RuntimeException(s"service $serviceName not found")
}

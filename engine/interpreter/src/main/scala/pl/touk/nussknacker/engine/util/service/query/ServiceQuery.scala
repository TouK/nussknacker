package pl.touk.nussknacker.engine.util.service.query

import cats.Monad
import cats.implicits._
import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ToCollect, TransmissionNames}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.language.higherKinds
import scala.concurrent.{ExecutionContext, Future}

// TODO: Processes using Flink's RuntimeContex, ex. metrics throws NPE, but in another thread, so service works.
class ServiceQuery(modelData: ModelData) {

  import ServiceQuery._

  private val evaluator = ExpressionEvaluator.unOptimizedEvaluator(modelData)

  private val ctx = Context("")

  def invoke(serviceName: String, args: (String, Expression)*)
            (implicit executionContext: ExecutionContext): Future[QueryResult] = {
    val params = args.map(pair => evaluatedparam.Parameter(pair._1, pair._2)).toList
    invoke(serviceName, params)
  }

  def invoke(serviceName: String,
             params: List[evaluatedparam.Parameter])
            (implicit executionContext: ExecutionContext): Future[QueryResult] = {

    //we create new definitions each time, to avoid lifecycle problems
    val definitions = modelData.withThisAsContextClassLoader {
      ProcessDefinitionExtractor.extractObjectWithMethods(modelData.configCreator, ProcessObjectDependencies(modelData.processConfig, modelData.objectNaming))
    }

    val collector = new QueryServiceInvocationCollector()
    val compiler = new NodeCompiler(definitions, ExpressionCompiler.withoutOptimization(modelData),
      modelData.modelClassLoader.classLoader, collector)


    withOpenedService(serviceName, definitions) { 

      val variablesPreparer = GlobalVariablesPreparer(definitions.expressionConfig)
      val validationContext = variablesPreparer.emptyValidationContext(metaData)

      val compiled = compiler.compileService(ServiceRef(serviceName, params), validationContext, None)(NodeId(""), metaData)
      compiled.compiledObject.map { service =>
          service.invoke(ctx, evaluator)._2.map(QueryResult(_, collector.retrieveResults()))
      }.valueOr(e => Future.failed(ServiceInvocationException(e)))
    }

  }

  private def withOpenedService[T](serviceName: String, definitions: ProcessDefinition[ObjectWithMethodDef])
                                  (action: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val service = definitions.services.get(serviceName).map(_.obj) match {
      case Some(lifecycle: Lifecycle) => lifecycle
      case _ => throw ServiceNotFoundException(s"Service $serviceName not found")
    }
    service.open(jobData)
    val result = action
    result.onComplete(_ => service.close())
    result
  }
}

object ServiceQuery {

  case class QueryServiceResult(name: String, result: Any)

  case class QueryResult(result: Any, collectedResults: List[QueryServiceResult])

  private implicit val nodeId: NodeId = NodeId("defaultNodeId")

  private implicit val metaData: MetaData = MetaData(
    id = "testProcess",
    typeSpecificData = StandaloneMetaData(None),
    additionalFields = None
  )

  private val jobData = JobData(metaData, ProcessVersion.empty)

  class QueryServiceInvocationCollector extends ResultCollector {

    private var queryResults = List[QueryServiceResult]()

    override def collectWithResponse[A, F[_]:Monad](contextId: ContextId, nodeId: NodeId, serviceRef: String, request: => ToCollect, mockValue: Option[A], action: => F[CollectableAction[A]], names: TransmissionNames): F[A] = {
      add(request, names.invocationName)
      action.map { collectableAction =>
        add(collectableAction.toCollect(), names.resultName)
        collectableAction.result
      }
    }

    def add(testInvocation: Any, name: String): Unit = synchronized {
      queryResults = QueryServiceResult(name, testInvocation) :: queryResults
    }

    def retrieveResults(): List[QueryServiceResult] = synchronized {
      queryResults
    }

  }

  //TODO: remove this and return ValidatedNel, let NK-UI handle error display...
  case class ServiceInvocationException(nel: NonEmptyList[ProcessCompilationError])
    extends IllegalArgumentException(nel.toList.mkString(", "))

  case class ServiceNotFoundException(message: String)
    extends IllegalArgumentException(message)
}
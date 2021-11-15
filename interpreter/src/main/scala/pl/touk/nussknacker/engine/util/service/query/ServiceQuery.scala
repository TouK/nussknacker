package pl.touk.nussknacker.engine.util.service.query

import cats.Monad
import cats.data.NonEmptyList
import cats.implicits._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, RunMode}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext, IncContextIdGenerator}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ToCollect, TransmissionNames}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
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
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

// TODO: Processes using Flink's RuntimeContex, ex. metrics throws NPE, but in another thread, so service works.
class ServiceQuery(modelData: ModelData) {

  import ServiceQuery._

  private val evaluator = ExpressionEvaluator.unOptimizedEvaluator(modelData)

  def invoke(serviceName: String, args: (String, Expression)*)
            (implicit executionContext: ExecutionContext): Future[QueryResult] =
    invoke(serviceName, localVariables = Map.empty, args = args: _*)

  def invoke(serviceName: String, localVariables: Map[String, (Any, TypingResult)], args: (String, Expression)*)
            (implicit executionContext: ExecutionContext): Future[QueryResult] = {
    val params = args.map(pair => evaluatedparam.Parameter(pair._1, pair._2)).toList
    invoke(serviceName, localVariables, params)
  }

  def invoke(serviceName: String,
             localVariables: Map[String, (Any, TypingResult)] = Map.empty,
             params: List[evaluatedparam.Parameter])
            (implicit executionContext: ExecutionContext): Future[QueryResult] = {

    //we create new definitions each time, to avoid lifecycle problems
    val definitions = modelData.withThisAsContextClassLoader {
      ProcessDefinitionExtractor.extractObjectWithMethods(modelData.configCreator, ProcessObjectDependencies(modelData.processConfig, modelData.objectNaming))
    }

    val collector = new QueryServiceInvocationCollector()
    val compiler = new NodeCompiler(definitions, ExpressionCompiler.withoutOptimization(modelData),
      modelData.modelClassLoader.classLoader, collector, RunMode.Normal)


    withOpenedService(serviceName, definitions) {

      val variablesPreparer = GlobalVariablesPreparer(definitions.expressionConfig)
      val validationContext = variablesPreparer.validationContextWithLocalVariables(metaData, localVariables.mapValues(_._2))
      val ctx = Context("", localVariables.mapValues(_._1), None)
      implicit val runMode: RunMode = RunMode.Normal

      val compiled = compiler.compileService(ServiceRef(serviceName, params), validationContext, Some(OutputVar.enricher("output")))(NodeId(""), metaData)
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
    service.open(runtimeContextForService(jobData))
    val result = action
    result.onComplete(_ => service.close())
    result
  }

  private def runtimeContextForService(tjobData: JobData) = {
    new EngineRuntimeContext {
      override def jobData: JobData = tjobData

      override def metricsProvider: MetricsProviderForScenario = NoOpMetricsProviderForScenario

      override def contextIdGenerator(nodeId: String): ContextIdGenerator = new IncContextIdGenerator(jobData.metaData.id + "-" + nodeId)
    }
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

  private val jobData = JobData(metaData, ProcessVersion.empty, DeploymentData.empty)

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

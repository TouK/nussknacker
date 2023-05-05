package pl.touk.nussknacker.engine.lite

import cats.data.ValidatedNel
import cats.{Id, ~>}
import cats.implicits._
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnknownProperty
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessName, Source, SourceTestSupport, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord, ScenarioTestParametersRecord, TestRecord}
import pl.touk.nussknacker.engine.api.{Context, JobData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.testmode._
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.higherKinds

trait TestRunner {
  def runTest[T](modelData: ModelData,
                 scenarioTestData: ScenarioTestData,
                 process: CanonicalProcess,
                 variableEncoder: Any => T): TestResults[T]
}

//TODO: integrate with Engine somehow?
class InterpreterTestRunner[F[_] : InterpreterShape : CapabilityTransformer : EffectUnwrapper, Input, Res <: AnyRef] extends TestRunner {

  def runTest[T](modelData: ModelData,
                 scenarioTestData: ScenarioTestData,
                 process: CanonicalProcess,
                 variableEncoder: Any => T): TestResults[T] = {

    //TODO: probably we don't need statics here, we don't serialize stuff like in Flink
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    //in tests we don't send metrics anywhere
    val testContext = LiteEngineRuntimeContextPreparer.noOp.prepare(testJobData(process))
    val componentUseCase: ComponentUseCase = ComponentUseCase.TestRuntime

    val expressionEvaluator: (Expression, Parameter, NodeId) => ValidatedNel[PartSubGraphCompilationError, AnyRef] = {
      val validationContext = GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig).emptyValidationContext(process.metaData)
      val evaluator = ExpressionEvaluator.unOptimizedEvaluator(modelData)
      val dumbContext = Context("dumb", Map.empty, None)
      val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData.expressionCompilerModelData)
      (expression: Expression, parameter: Parameter, nodeId: NodeId) => {
        expressionCompiler
          .compile(expression, Some(parameter.name), validationContext, parameter.typ)(nodeId)
          .map { typedExpression =>
            val param = evaluatedparam.Parameter(typedExpression, parameter)
            evaluator.evaluateParameter(param, dumbContext)(nodeId, process.metaData).value
          }
      }
    }


    //FIXME: validation??
    val scenarioInterpreter = ScenarioInterpreterFactory.createInterpreter[F, Input, Res](process, modelData,
      additionalListeners = List(collectingListener), new TestServiceInvocationCollector(collectingListener.runId), componentUseCase
    )(SynchronousExecutionContext.ctx, implicitly[InterpreterShape[F]], implicitly[CapabilityTransformer[F]]) match {
      case Valid(interpreter) => interpreter
      case Invalid(errors) => throw new IllegalArgumentException("Error during interpreter preparation: " + errors.toList.mkString(", "))
    }

    def getSourceById(sourceId: SourceId): Source = scenarioInterpreter.sources.getOrElse(sourceId,
      throw new IllegalArgumentException(s"Found source '${sourceId.value}' in a test record but is not present in the scenario"))

    val inputs = ScenarioInputBatch(scenarioTestData.testRecords.map {
      case ScenarioTestJsonRecord(NodeId(sourceIdValue), testRecord) =>
        val sourceId = SourceId(sourceIdValue)
        val source = getSourceById(sourceId)
        sourceId -> prepareRecordForTest[Input](source, testRecord)
      case ScenarioTestParametersRecord(nodeId@NodeId(sourceIdValue), parameterExpressions) =>
        val sourceId = SourceId(sourceIdValue)
        val source = getSourceById(sourceId)
        sourceId -> prepareRecordForTest[Input](source, parameterExpressions, expressionEvaluator)(nodeId)
    })

    try {
      scenarioInterpreter.open(testContext)

      val results = implicitly[EffectUnwrapper[F]].apply(scenarioInterpreter.invoke(inputs))

      collectSinkResults(collectingListener.runId, results)
      collectingListener.results
    } finally {
      collectingListener.clean()
      scenarioInterpreter.close()
      testContext.close()
    }
  }

  private def testJobData(process: CanonicalProcess) = {
    // testing process may be unreleased, so it has no version
    val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version"))
    JobData(process.metaData, processVersion)
  }

  private def prepareRecordForTest[T](source: Source, parameterExpressions: Map[String, Expression],
                                      expressionEvaluator: (Expression, Parameter, NodeId) => ValidatedNel[PartSubGraphCompilationError, AnyRef]
                                     )(implicit nodeId: NodeId): T = {
    source match {
      case s: TestWithParametersSupport[T@unchecked] =>
        val parameterTypingResults = s.testParametersDefinition.map { param =>
          parameterExpressions.get(param.name) match {
            case Some(expression) => expressionEvaluator(expression, param, nodeId).map(e => param.name -> e)
            case None => UnknownProperty(param.name).invalidNel
          }
        }
        parameterTypingResults.sequence match {
          case Valid(evaluatedParams) => s.parametersToTestData(evaluatedParams.toMap)
          case Invalid(errors) => throw new IllegalArgumentException(errors.toList.mkString(", "))
        }
      case other => throw new IllegalArgumentException(s"Source ${other.getClass} cannot be stubbed - it doesn't provide test with parameters")
    }
  }

  private def prepareRecordForTest[T](source: Source, testRecord: TestRecord): T = {
    source match {
      case s: SourceTestSupport[T@unchecked] => s.testRecordParser.parse(testRecord)
      case other => throw new IllegalArgumentException(s"Source ${other.getClass} cannot be stubbed - it doesn't provide test data parser")
    }
  }

  private def collectSinkResults(runId: TestRunId, results: ResultType[EndResult[Res]]): Unit = {
    val successfulResults = results.value
    successfulResults.foreach { result =>
      val node = result.nodeId.id
      SinkInvocationCollector(runId, node, node).collect(result.context, result.result)
    }
  }

}

object TestRunner {

  type EffectUnwrapper[F[_]] = F ~> Id

  private val scenarioTimeout = 10 seconds

  //TODO: should we consider configurable timeout?
  implicit val unwrapper: EffectUnwrapper[Future] = new EffectUnwrapper[Future] {
    override def apply[Y](eff: Future[Y]): Y = Await.result(eff, scenarioTimeout)
  }

}

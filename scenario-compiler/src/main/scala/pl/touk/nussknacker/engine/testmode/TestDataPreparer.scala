package pl.touk.nussknacker.engine.testmode

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{Context, JobData, LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnknownProperty
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.test.{
  ScenarioTestParametersRecord,
  ScenarioTestRecord,
  ScenarioTestSourceSpecificFormatJsonRecord,
  TestRecord
}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  EvaluableLazyParameterCreator,
  EvaluableLazyParameterCreatorDeps
}
import pl.touk.nussknacker.engine.compiledgraph.CompiledParameter
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.expression.{ExpectedType, ExpressionEvaluator}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

class TestDataPreparer(
    classloader: ClassLoader,
    expressionConfig: ExpressionConfigDefinition,
    dictRegistry: EngineDictRegistry,
    classDefinitionSet: ClassDefinitionSet,
    jobData: JobData
) {

  private lazy val dummyContext            = Context.dummy
  private lazy val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)
  private lazy val validationContext = globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData)
  private lazy val evaluator: ExpressionEvaluator = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)
  private lazy val expressionCompiler: ExpressionCompiler =
    ExpressionCompiler.withoutOptimization(classloader, dictRegistry, expressionConfig, classDefinitionSet, evaluator)

  private val lazyParameterDeps = new EvaluableLazyParameterCreatorDeps(expressionCompiler, evaluator, jobData)

  def resolveParam[T <: AnyRef](lazyParameterCreator: EvaluableLazyParameterCreator[T]): LazyParameter[T] = {
    lazyParameterCreator.create(lazyParameterDeps)
  }

  def prepareRecordsForTest[T](source: Source, records: List[ScenarioTestRecord]): List[T] = {
    val jsonRecordList       = records.collect { case r: ScenarioTestSourceSpecificFormatJsonRecord => r }
    val parametersRecordList = records.collect { case r: ScenarioTestParametersRecord => r }
    // We don't handle ScenarioTestCommonFormatJsonRecord - it is handled by CommonTestDataFormatComponentExecutorStubber
    val testRecordsFromJsonRecords = (jsonRecordList, source) match {
      case (Nil, _) => List.empty
      case (_, s: SourceTestSupport[T @unchecked]) =>
        val parser = s.testRecordParser
        ThreadUtils.withContextClassLoader(classloader) {
          parser.parse(jsonRecordList.map(record => TestRecord(record.record, record.timestamp)))
        }
      case (_, other) =>
        throw new IllegalArgumentException(
          s"Source ${other.getClass} cannot be stubbed - it doesn't provide test data parser"
        )
    }
    val testRecordsFromParametersRecords = (parametersRecordList, source) match {
      case (Nil, _) => List.empty
      case (_, s: TestWithParametersSupport[T @unchecked]) =>
        parametersRecordList.map { record =>
          implicit val implicitNodeId: NodeId = record.sourceId
          val parameterTypingResults = s.testParametersDefinition.collect { param =>
            record.parameterExpressions.get(param.name) match {
              case Some(expression)          => evaluateExpression(expression, param).map(e => param.name -> e)
              case None if !param.isOptional => UnknownProperty(param.name).invalidNel
            }
          }
          parameterTypingResults.sequence match {
            case Valid(evaluatedParams) => s.parametersToTestData(evaluatedParams.toMap)
            case Invalid(errors)        => throw new IllegalArgumentException(errors.toList.mkString(", "))
          }
        }
      case (_, other) =>
        throw new IllegalArgumentException(
          s"Source ${other.getClass} cannot be stubbed - it doesn't provide test with parameters support"
        )
    }
    testRecordsFromJsonRecords ++ testRecordsFromParametersRecords
  }

  private def evaluateExpression(expression: Expression, parameter: Parameter)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, AnyRef] = {
    expressionCompiler
      .compile(expression, Some(parameter.name), validationContext, ExpectedType.fromParameter(parameter))(nodeId)
      .map { typedExpression =>
        val param = CompiledParameter(typedExpression, parameter)
        evaluator.evaluateParameter(param, dummyContext)(nodeId, jobData).toTry.get.value
      }
  }

}

object TestDataPreparer {

  def apply(modelData: ModelData, jobData: JobData): TestDataPreparer =
    new TestDataPreparer(
      modelData.modelClassLoader,
      modelData.modelDefinition.expressionConfig,
      modelData.engineDictRegistry,
      modelData.modelDefinitionWithClasses.classDefinitions,
      jobData
    )

}

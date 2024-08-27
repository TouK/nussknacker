package pl.touk.nussknacker.engine.testmode

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnknownProperty
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestJsonRecord, ScenarioTestParametersRecord, ScenarioTestRecord}
import pl.touk.nussknacker.engine.api.{Context, MetaData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.CompiledParameter
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

class TestDataPreparer(
    classloader: ClassLoader,
    expressionConfig: ExpressionConfigDefinition,
    dictRegistry: EngineDictRegistry,
    classDefinitionSet: ClassDefinitionSet,
    metaData: MetaData
) {

  private lazy val dumbContext             = Context("dumb", Map.empty, None)
  private lazy val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)
  private lazy val validationContext = globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(metaData)
  private lazy val evaluator: ExpressionEvaluator = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)
  private lazy val expressionCompiler: ExpressionCompiler =
    ExpressionCompiler.withoutOptimization(classloader, dictRegistry, expressionConfig, classDefinitionSet, evaluator)

  def prepareRecordForTest[T](source: Source, record: ScenarioTestRecord): T = {
    implicit val implicitNodeId: NodeId = record.sourceId
    (source, record) match {
      case (s: SourceTestSupport[T @unchecked], jsonRecord: ScenarioTestJsonRecord) =>
        s.testRecordParser.parse(jsonRecord.record)
      case (s: TestWithParametersSupport[T @unchecked], parametersRecord: ScenarioTestParametersRecord) =>
        val parameterTypingResults = s.testParametersDefinition.collect { param =>
          parametersRecord.parameterExpressions.get(param.name) match {
            case Some(expression)          => evaluateExpression(expression, param).map(e => param.name -> e)
            case None if !param.isOptional => UnknownProperty(param.name).invalidNel
          }
        }
        parameterTypingResults.sequence match {
          case Valid(evaluatedParams) => s.parametersToTestData(evaluatedParams.toMap)
          case Invalid(errors)        => throw new IllegalArgumentException(errors.toList.mkString(", "))
        }
      case (other, _: ScenarioTestJsonRecord) =>
        throw new IllegalArgumentException(
          s"Source ${other.getClass} cannot be stubbed - it doesn't provide test data parser"
        )
      case (other, _: ScenarioTestParametersRecord) =>
        throw new IllegalArgumentException(
          s"Source ${other.getClass} cannot be stubbed - it doesn't provide test with parameters"
        )
    }
  }

  private def evaluateExpression(expression: Expression, parameter: Parameter)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, AnyRef] = {
    expressionCompiler
      .compile(expression, Some(parameter.name), validationContext, parameter.typ)(nodeId)
      .map { typedExpression =>
        val param = CompiledParameter(typedExpression, parameter)
        evaluator.evaluateParameter(param, dumbContext)(nodeId, metaData).value
      }
  }

}

object TestDataPreparer {

  def apply(modelData: ModelData, process: CanonicalProcess): TestDataPreparer =
    new TestDataPreparer(
      modelData.modelClassLoader.classLoader,
      modelData.modelDefinition.expressionConfig,
      modelData.engineDictRegistry,
      modelData.modelDefinitionWithClasses.classDefinitions,
      process.metaData
    )

}

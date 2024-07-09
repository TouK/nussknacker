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
import scala.reflect.runtime.universe._

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
    ExpressionCompiler.withoutOptimization(classloader, dictRegistry, expressionConfig, classDefinitionSet)

  // TODO local: refactor List[ScenarioTestRecord] to ADT?
  def prepareRecordForTest[T](source: Source, records: List[ScenarioTestRecord]): List[T] = {
    if (records.forall(a => a.isInstanceOf[ScenarioTestJsonRecord])) {
      val jsonRecords = records.asInstanceOf[List[ScenarioTestJsonRecord]].map(_.record)
      source match {
        case s: SourceTestSupport[T @unchecked] => s.testRecordParser.parse(jsonRecords)
        case other =>
          throw new IllegalArgumentException(
            s"Source ${other.getClass} cannot be stubbed - it doesn't provide test data parser"
          )
      }
    } else if (records.forall(a => a.isInstanceOf[ScenarioTestParametersRecord])) {
      val parametersRecords = records.asInstanceOf[List[ScenarioTestParametersRecord]]
      source match {
        case s: TestWithParametersSupport[T @unchecked] =>
          parametersRecords.map { record =>
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
        case other =>
          throw new IllegalArgumentException(
            s"Source ${other.getClass} cannot be stubbed - it doesn't provide test with parameters"
          )
      }
    } else {
      throw new IllegalStateException("TODO error message") // TODO local
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

package pl.touk.nussknacker.engine.process.compiler

import cats.data.ValidatedNel
import cats.implicits._
import cats.data.Validated.{Invalid, Valid}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnknownProperty
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ContextInitializer, ProcessObjectDependencies, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord, ScenarioTestParametersRecord}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{Context, MetaData, NodeId, ProcessListener}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.process.{FlinkIntermediateRawSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EmptySource}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListener
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

class TestFlinkProcessCompiler(modelData: ModelData,
                               collectingListener: ResultsCollectingListener,
                               process: CanonicalProcess,
                               scenarioTestData: ScenarioTestData)
  extends StubbedFlinkProcessCompiler(process, modelData.configCreator, modelData.processConfig, diskStateBackendSupport = false, modelData.objectNaming, ComponentUseCase.TestRuntime) {

  override protected def adjustListeners(defaults: List[ProcessListener], processObjectDependencies: ProcessObjectDependencies): List[ProcessListener] = {
    collectingListener :: defaults
  }

  private val dumbContext = Context("dumb", Map.empty, None)
  private lazy val validationContext = GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig).emptyValidationContext(process.metaData)
  private lazy val evaluator = ExpressionEvaluator.unOptimizedEvaluator(modelData)
  private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
    case spel: SpelExpressionParser => spel.typingDictLabels
  }

  override protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(sourceFactory, (originalSource, returnTypeOpt, nodeId) => {
      originalSource match {
        case sourceWithTestSupport: FlinkSourceTestSupport[Object@unchecked] =>
          val samples: List[Object] = collectSamples(originalSource, sourceWithTestSupport, nodeId)
          val returnType = returnTypeOpt.getOrElse(throw new IllegalStateException(s"${sourceWithTestSupport.getClass} extends FlinkSourceTestSupport and has no return type"))
          sourceWithTestSupport match {
            case providerWithTransformation: FlinkIntermediateRawSource[Object@unchecked] =>
              new CollectionSource[Object](samples, sourceWithTestSupport.timestampAssignerForTest, returnType)(providerWithTransformation.typeInformation) {
                override val contextInitializer: ContextInitializer[Object] = providerWithTransformation.contextInitializer
              }
            case _ =>
              new CollectionSource[Object](samples, sourceWithTestSupport.timestampAssignerForTest, returnType)(sourceWithTestSupport.typeInformation)
          }
        case _ =>
          EmptySource[Object](returnTypeOpt.getOrElse(Unknown))(TypeInformation.of(classOf[Object]))
      }
    })
  }

  override protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef = service

  override protected def exceptionHandler(metaData: MetaData,
                                          processObjectDependencies: ProcessObjectDependencies,
                                          listeners: Seq[ProcessListener],
                                          classLoader: ClassLoader): FlinkExceptionHandler = componentUseCase match {
    case ComponentUseCase.TestRuntime => new FlinkExceptionHandler(metaData, processObjectDependencies, listeners, classLoader) {
      override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()
      override val consumer: FlinkEspExceptionConsumer = _ => {}
    }
    case _ => super.exceptionHandler(metaData, processObjectDependencies, listeners, classLoader)
  }

  private def expressionEvaluator(expression: Expression, parameter: Parameter, nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, AnyRef] = {
    expressionCompiler
      .compile(expression, Some(parameter.name), validationContext, parameter.typ)(nodeId)
      .map { typedExpression =>
        val param = evaluatedparam.Parameter(typedExpression, parameter)
        evaluator.evaluateParameter(param, dumbContext)(nodeId, process.metaData).value
      }
  }

  private def collectSamples(originalSource: Any, sourceWithTestSupport: FlinkSourceTestSupport[Object@unchecked], nodeId: NodeId): List[Object] = {
    val scenarioJsonRecords = scenarioTestData.testRecords.collect { case testRecord: ScenarioTestJsonRecord => testRecord }.filter(_.sourceId == nodeId)
    val scenarioParamRecords = scenarioTestData.testRecords.collect { case testRecord: ScenarioTestParametersRecord => testRecord }.filter(_.sourceId == nodeId)

    if (scenarioJsonRecords.nonEmpty && scenarioParamRecords.nonEmpty)
      throw new IllegalStateException(s"${sourceWithTestSupport.getClass} cannot test both with ScenarioTestJsonRecord and ScenarioTestParametersRecord at the same time")

    if (scenarioJsonRecords.nonEmpty) scenarioJsonRecords.map(jsonRecord => sourceWithTestSupport.testRecordParser.parse(jsonRecord.record))
    else if (scenarioParamRecords.nonEmpty) {
      originalSource match {
        case sourceTestWithParameters: TestWithParametersSupport[Object@unchecked] =>
          scenarioParamRecords.map { paramRecord => prepareDataForTest(sourceTestWithParameters, paramRecord.parameterExpressions, nodeId) }
        case _ => throw new IllegalStateException(s"${sourceWithTestSupport.getClass} does not extends TestWithParametersSupport but uses ScenarioTestParametersRecord for tests.")
      }
    }
    else Nil
  }

  private def prepareDataForTest[T](sourceTestWithParameters: TestWithParametersSupport[T], parameterExpressions: Map[String, Expression], sourceId: NodeId): T = {
    val listOfExpressions =  sourceTestWithParameters.testParametersDefinition.map { param =>
      parameterExpressions.get(param.name) match {
        case Some(expression) => expressionEvaluator(expression, param, sourceId).map(e => param.name -> e)
        case None => UnknownProperty(param.name)(sourceId).invalidNel
      }
    }

    listOfExpressions.sequence match {
      case Valid(evaluatedParams) => sourceTestWithParameters.parametersToTestData(evaluatedParams.toMap)
      case Invalid(errors) => throw new IllegalArgumentException(errors.toList.mkString(", "))
    }
  }

}




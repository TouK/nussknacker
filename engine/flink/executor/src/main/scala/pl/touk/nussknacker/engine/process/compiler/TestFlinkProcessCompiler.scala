package pl.touk.nussknacker.engine.process.compiler

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits._
import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.{ExpressionCompilerModelData, ModelData}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnknownProperty
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord, ScenarioTestParametersRecord}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{Context, NodeId, ProcessListener}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ModelDefinitionWithTypes
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.flink.api.process.{FlinkIntermediateRawSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EmptySource}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListener
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

object TestFlinkProcessCompiler {

  def apply(modelData: ModelData,
            process: CanonicalProcess,
            scenarioTestData: ScenarioTestData,
            collectingListener: ResultsCollectingListener): FlinkProcessCompiler = {
    TestFlinkProcessCompiler(
      modelData.modelDefinitionWithTypes,
      modelData.configCreator,
      modelData.processConfig,
      modelData.objectNaming,
      process,
      scenarioTestData,
      collectingListener,
      // TODO: we should use DictServicesFactory.createEngineDictRegistry instead
      modelData.dictServices.dictRegistry.toEngineRegistry)
  }

  def apply(modelDefinitionWithTypes: ModelDefinitionWithTypes,
            creator: ProcessConfigCreator,
            inputConfigDuringExecution: Config,
            objectNaming: ObjectNaming,
            process: CanonicalProcess,
            scenarioTestData: ScenarioTestData,
            collectingListener: ResultsCollectingListener,
            dictRegistry: EngineDictRegistry): FlinkProcessCompiler = {
    val stubber = new ComponentsImplementationStubber(process) {

      private lazy val dumbContext = Context("dumb", Map.empty, None)
      private lazy val globalVariablesPreparer: GlobalVariablesPreparer = GlobalVariablesPreparer(modelDefinitionWithTypes.modelDefinition.expressionConfig)
      private lazy val validationContext = globalVariablesPreparer.emptyValidationContext(process.metaData)
      private lazy val evaluator = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)
      private lazy val expressionCompiler = ExpressionCompiler.withoutOptimization(
        ExpressionCompilerModelData(modelDefinitionWithTypes, dictRegistry, creator.getClass.getClassLoader))

      override protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef = {
        sourceFactory.withImplementationInvoker(new StubbedComponentImplementationInvoker(sourceFactory) {
          override def handleInvoke(originalSource: Any, returnTypeOpt: Option[typing.TypingResult], nodeId: NodeId): Any = {
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
          }
        })
      }

      private def collectSamples(originalSource: Any, sourceWithTestSupport: FlinkSourceTestSupport[Object@unchecked], nodeId: NodeId): List[Object] = {
        scenarioTestData.testRecords.filter(_.sourceId == nodeId).map {
          case testRecord: ScenarioTestJsonRecord =>
            sourceWithTestSupport.testRecordParser.parse(testRecord.record)
          case testRecord: ScenarioTestParametersRecord =>
            originalSource match {
              case sourceTestWithParameters: TestWithParametersSupport[Object@unchecked] =>
                prepareDataForTest(sourceTestWithParameters, testRecord.parameterExpressions, nodeId)
              case _ => throw new IllegalStateException(s"${sourceWithTestSupport.getClass} does not extends TestWithParametersSupport but uses ScenarioTestParametersRecord for tests.")
            }
        }
      }

      private def prepareDataForTest[T](sourceTestWithParameters: TestWithParametersSupport[T], parameterExpressions: Map[String, Expression], sourceId: NodeId): T = {
        val listOfExpressions = sourceTestWithParameters.testParametersDefinition.map { param =>
          parameterExpressions.get(param.name) match {
            case Some(expression) => evaluateExpression(expression, param, sourceId).map(e => param.name -> e)
            case None => UnknownProperty(param.name)(sourceId).invalidNel
          }
        }

        listOfExpressions.sequence match {
          case Valid(evaluatedParams) => sourceTestWithParameters.parametersToTestData(evaluatedParams.toMap)
          case Invalid(errors) => throw new IllegalArgumentException(errors.toList.mkString(", "))
        }
      }

      private def evaluateExpression(expression: Expression, parameter: Parameter, nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, AnyRef] = {
        expressionCompiler
          .compile(expression, Some(parameter.name), validationContext, parameter.typ)(nodeId)
          .map { typedExpression =>
            val param = evaluatedparam.Parameter(typedExpression, parameter)
            evaluator.evaluateParameter(param, dumbContext)(nodeId, process.metaData).value
          }
      }

      override protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef = service

    }
    val stubbedDefinitions = stubber.stubImplementations(modelDefinitionWithTypes)
    new FlinkProcessCompiler(stubbedDefinitions, creator, inputConfigDuringExecution, diskStateBackendSupport = false, objectNaming, ComponentUseCase.TestRuntime) {
      override protected def adjustListeners(defaults: List[ProcessListener], processObjectDependencies: ProcessObjectDependencies): List[ProcessListener] = {
        collectingListener :: defaults
      }
    }
  }

}



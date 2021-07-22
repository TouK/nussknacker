package pl.touk.nussknacker.engine.definition

import java.util.Collections
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.conversion.ProcessConfigCreatorMapping
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory.NoParamExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.dict.{DictServicesFactoryLoader, SimpleDictRegistry}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class LazyParameterSpec extends FunSuite with Matchers {

  test("should parse expression for param once") {
    implicit val lazyParameterInterpreter: LazyParameterInterpreter = prepareInterpreter

    checkParameterInvokedOnceAfterTransform(identity)
  }

  test("should parse expression for mapped param once") {
    implicit val lazyParameterInterpreter: LazyParameterInterpreter = prepareInterpreter

    checkParameterInvokedOnceAfterTransform(_.map[Integer]((i: Integer) => i + 1: Integer))
  }

  test("should parse expression for product once") {
    implicit val lazyParameterInterpreter: LazyParameterInterpreter = prepareInterpreter

    checkParameterInvokedOnceAfterTransform(_.product(lazyParameterInterpreter.pure("333", Typed[String])))
  }


  private def checkParameterInvokedOnceAfterTransform(transform: CompilerLazyParameter[Integer] => LazyParameter[_<:AnyRef])
                                                     (implicit lazyParameterInterpreter: LazyParameterInterpreter) = {

    var invoked = 0
    val evalParameter = new CompilerLazyParameter[Integer] {
      override def prepareEvaluator(deps: CompilerLazyParameterInterpreter)(implicit ec: ExecutionContext): Context => Future[Integer] = {
        invoked += 1
        _ => {
          Future.successful(123)
        }
      }

      override def returnType: typing.TypingResult = Typed[Integer]
    }

    val mappedParam = transform(evalParameter)
    val fun = lazyParameterInterpreter.syncInterpretationFunction(mappedParam)
    fun(Context(""))
    fun(Context(""))

    invoked shouldEqual 1
  }

  private def prepareInterpreter = {
    val exprDef = ExpressionDefinition(Map.empty, List.empty, List.empty, LanguageConfiguration.default, optimizeCompilation = false, strictTypeChecking = true, Map.empty,
      hideMetaVariable = false, strictMethodsChecking = true, staticMethodInvocationsChecking = false, disableMethodExecutionForUnknown = false)
    val processDef = ProcessDefinition(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty,
      ObjectWithMethodDef.withEmptyConfig(new NoParamExceptionHandlerFactory(_ => null), ProcessObjectDefinitionExtractor.exceptionHandler), exprDef, ClassExtractionSettings.Default)
    val lazyInterpreterDeps = prepareLazyInterpreterDeps(processDef)

    new CompilerLazyParameterInterpreter {
      override def deps: LazyInterpreterDependencies = lazyInterpreterDeps
      override def metaData: MetaData = MetaData("proc1", StreamMetaData())
      override def close(): Unit = {}
    }
  }

  def prepareLazyInterpreterDeps(definitions: ProcessDefinition[ObjectWithMethodDef]): LazyInterpreterDependencies = {
    val expressionEvaluator =  ExpressionEvaluator.unOptimizedEvaluator(GlobalVariablesPreparer(definitions.expressionConfig))
    val typeDefinitionSet = TypeDefinitionSet(ProcessDefinitionExtractor.extractTypes(definitions))
    val expressionCompiler = ExpressionCompiler.withOptimization(getClass.getClassLoader,
      new SimpleDictRegistry(Map.empty), definitions.expressionConfig,
      ClassExtractionSettings.Default, typeDefinitionSet)
    LazyInterpreterDependencies(expressionEvaluator, expressionCompiler, 10.seconds)
  }

}

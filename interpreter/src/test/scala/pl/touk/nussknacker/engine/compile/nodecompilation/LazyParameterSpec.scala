package pl.touk.nussknacker.engine.compile.nodecompilation

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.lazyparam.EvaluableLazyParameter
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.component.ComponentWithRuntimeLogicFactory
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionWithClasses}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class LazyParameterSpec extends AnyFunSuite with Matchers {

  test("should parse expression for param once") {
    checkParameterInvokedOnceAfterTransform(identity)
  }

  test("should parse expression for mapped param once") {
    checkParameterInvokedOnceAfterTransform(_.map[Integer]((i: Integer) => i + 1: Integer))
  }

  test("should parse expression for product once") {
    checkParameterInvokedOnceAfterTransform(_.product(LazyParameter.pure("333", Typed[String])))
  }

  test("should sequence evaluations") {
    val params = List[LazyParameter[AnyRef]](
      LazyParameter.pureFromDetailedType[Integer](123),
      LazyParameter.pureFromDetailedType("foo")
    )

    val tupled = LazyParameter.sequence(
      params,
      (seq: List[AnyRef]) => (seq.head, seq(1)),
      seq => Typed.genericTypeClass[(AnyRef, AnyRef)](seq)
    )
    val expectedType =
      Typed.genericTypeClass[(AnyRef, AnyRef)](List(Typed.fromDetailedType[Integer], Typed.fromDetailedType[String]))
    tupled.returnType shouldEqual expectedType

    val lazyParameterInterpreter = prepareInterpreter
    val fun                      = lazyParameterInterpreter.syncInterpretationFunction(tupled)
    val result                   = fun(Context(""))

    result shouldEqual (123, "foo")
  }

  private def checkParameterInvokedOnceAfterTransform(
      transform: LazyParameter[Integer] => LazyParameter[_ <: AnyRef]
  ) = {

    var invoked = 0
    val evalParameter = new EvaluableLazyParameter[Integer] {
      override def prepareEvaluator(
          deps: LazyParameterInterpreter
      )(implicit ec: ExecutionContext): Context => Future[Integer] = {
        invoked += 1
        _ => {
          Future.successful(123)
        }
      }

      override def returnType: typing.TypingResult = Typed[Integer]
    }

    val mappedParam              = transform(evalParameter)
    val lazyParameterInterpreter = prepareInterpreter
    val fun                      = lazyParameterInterpreter.syncInterpretationFunction(mappedParam)
    fun(Context(""))
    fun(Context(""))

    invoked shouldEqual 1
  }

  private def prepareInterpreter = {
    val exprDef = ExpressionConfigDefinition(
      Map.empty,
      List.empty,
      List.empty,
      LanguageConfiguration.default,
      optimizeCompilation = false,
      Map.empty,
      hideMetaVariable = false,
      strictMethodsChecking = true,
      staticMethodInvocationsChecking = false,
      methodExecutionForUnknownAllowed = false,
      dynamicPropertyAccessAllowed = false,
      spelExpressionExcludeList = SpelExpressionExcludeList.default,
      customConversionsProviders = List.empty
    )
    val processDef: ModelDefinition =
      ModelDefinition(List.empty, exprDef, ClassExtractionSettings.Default)
    val definitionWithTypes = ModelDefinitionWithClasses(processDef)
    val lazyInterpreterDeps = prepareLazyInterpreterDeps(definitionWithTypes)

    new CompilerLazyParameterInterpreter {
      override def deps: LazyInterpreterDependencies = lazyInterpreterDeps
      override def metaData: MetaData                = MetaData("proc1", StreamMetaData())
      override def close(): Unit                     = {}
    }
  }

  def prepareLazyInterpreterDeps(definitionWithTypes: ModelDefinitionWithClasses): LazyInterpreterDependencies = {
    import definitionWithTypes.modelDefinition
    val expressionEvaluator =
      ExpressionEvaluator.unOptimizedEvaluator(GlobalVariablesPreparer(modelDefinition.expressionConfig))
    val expressionCompiler = ExpressionCompiler.withOptimization(
      getClass.getClassLoader,
      new SimpleDictRegistry(Map.empty),
      modelDefinition.expressionConfig,
      definitionWithTypes.classDefinitions
    )
    LazyInterpreterDependencies(expressionEvaluator, expressionCompiler, 10.seconds)
  }

}

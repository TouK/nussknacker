package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compiledgraph.{CompiledAssertion, CompiledTestCase}
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

class AssertionVerifierSpec extends AnyFunSuite with Matchers {

  private val baseDefinition = ModelDefinitionBuilder.empty
    .withUnboundedStreamSource("sourceWithUnknown", Some(Unknown))
    .withService("enricher1", Some(Typed[String]), Parameter[String](ParameterName("par1")))
    .withSink("sink")
    .withGlobalVariable("TESTS", tests) // todo: move from here
    .build

  private val modelDefinitionWithClasses = ModelDefinitionWithClasses(baseDefinition)

  private val expressionCompiler = ExpressionCompiler.withOptimization(
    getClass.getClassLoader,
    new SimpleDictRegistry(Map.empty),
    modelDefinitionWithClasses.modelDefinition.expressionConfig,
    modelDefinitionWithClasses.classDefinitions,
    ExpressionEvaluator.unOptimizedEvaluator(
      GlobalVariablesPreparer.apply(modelDefinitionWithClasses.modelDefinition.expressionConfig)
    )
  )

  private val testCompiler = new TestCompiler(expressionCompiler)

  test("should run assertions on test nodes results") {
//    testCompiler.compile()
//    val verifier = new AssertionVerifierImpl()
//
//    CompiledTestCase("someTest", "dummy", Map.empty, Map(
//      NodeId("someNode") -> List(CompiledAssertion())
//    ))
////    verifier.verify()
  }

//  private def createTestCompiler(): TestCompiler = {
//  }

}

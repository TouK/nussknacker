package pl.touk.nussknacker.engine.variables

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{DynamicGlobalVariable, typing}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, StandardObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedDependencies}

class GlobalVariablesPreparerTest extends FunSuite with Matchers {

  test("should resolve real value and return type for dynamic variable") {
    val metaData = MetaData("test", StreamMetaData())
    val unusedMethodDef = MethodDefinition(name = "test", invocation = (_, _) => ???, orderedDependencies = new OrderedDependencies(Nil), returnType = Unknown, runtimeClass = classOf[Any], annotations = Nil)
    val varsWithMethodDef = Map("dynamicVar" -> StandardObjectWithMethodDef(TestDynamicGlobalVariable, methodDef = unusedMethodDef, objectDefinition = ObjectDefinition.noParam))

    val varsWithType = new GlobalVariablesPreparer(varsWithMethodDef, hideMetaVariable = true).prepareGlobalVariables(metaData)

    val varWithType = varsWithType("dynamicVar")
    varWithType.obj shouldBe 1
    varWithType.typ shouldBe Typed(classOf[Int])
  }

  object TestDynamicGlobalVariable extends DynamicGlobalVariable {
    override def value(metadata: MetaData): Any = 1

    override def returnType(metadata: MetaData): typing.TypingResult = Typed(classOf[Int])

    override def runtimeClass: Class[_] = classOf[java.util.List[_]]
  }
}

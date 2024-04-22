package pl.touk.nussknacker.engine.variables

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypedGlobalVariable, typing}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.definition.globalvariables.GlobalVariableDefinitionWithImplementation

class GlobalVariablesPreparerTest extends AnyFunSuite with Matchers {

  test("should resolve real value and return type for typed variable") {
    val metaData = MetaData("test", StreamMetaData())
    val varsWithMethodDef = Map(
      "typedVar" ->
        GlobalVariableDefinitionWithImplementation(TestTypedGlobalVariable)
    )

    val varsWithType =
      new GlobalVariablesPreparer(varsWithMethodDef, hideMetaVariable = true).prepareGlobalVariables(metaData)

    val varWithType = varsWithType("typedVar")
    varWithType.obj shouldBe 1
    varWithType.typ shouldBe Typed(classOf[Int])
  }

  object TestTypedGlobalVariable extends TypedGlobalVariable {
    override def value(metadata: MetaData): Any = 1

    override def returnType(metadata: MetaData): typing.TypingResult = Typed(classOf[Int])

    override def initialReturnType: TypingResult = Typed(classOf[java.util.List[_]])
  }

}

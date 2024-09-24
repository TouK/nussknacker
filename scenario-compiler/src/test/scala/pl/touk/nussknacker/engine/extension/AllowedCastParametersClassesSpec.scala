package pl.touk.nussknacker.engine.extension

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{
  ClassDefinition,
  ClassDefinitionSet,
  ClassDefinitionTestUtils,
  StaticMethodDefinition
}

class AllowedCastParametersClassesSpec extends AnyFunSuite with Matchers {
  object TestObj

  test("should return classes from ClassDefinitionSet except scala inner objects") {
    AllowedCastParametersClasses.apply(
      ClassDefinitionSet(
        Set(
          ClassDefinition(
            Typed.typedClass[String],
            Map(
              "toUpperCase" -> List(
                StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toUpperCase", None)
              )
            ),
            Map.empty
          ),
          ClassDefinitionTestUtils.DefaultExtractor.extract(classOf[TestObj.type])
        )
      )
    ) shouldBe new AllowedCastParametersClasses(
      Map(
        "java.lang.String" -> Typed[String]
      )
    )
  }

}

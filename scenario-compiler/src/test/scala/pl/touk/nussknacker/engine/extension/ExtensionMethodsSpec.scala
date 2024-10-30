package pl.touk.nussknacker.engine.extension

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet, StaticMethodDefinition}

class ExtensionMethodsSpec extends AnyFunSuite with Matchers {

  test(
    "should add extension methods to already existing definitions in ClassDefinitionSet"
  ) {
    val stringDefinition = ClassDefinition(
      Typed.typedClass[String],
      Map(
        "toUpperCase" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toUpperCase", None))
      ),
      Map.empty
    )
    val unknownDefinition = ClassDefinition(
      Unknown,
      Map(
        "toString" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toString", None))
      ),
      Map.empty
    )
    val definitionsSet = ClassDefinitionSet(Set(stringDefinition, unknownDefinition))

    ExtensionMethods
      .enrichWithExtensionMethods(
        definitionsSet
      )
      .classDefinitionsMap
      .map(e => e._1.getName -> e._2.methods.keys) shouldBe Map(
      "java.lang.String" -> Set(
        "isBigDecimal",
        "toBigDecimal",
        "toBigDecimalOrNull",
        "isBoolean",
        "toBoolean",
        "toBooleanOrNull",
        "isDouble",
        "toDouble",
        "toDoubleOrNull",
        "isLong",
        "toLong",
        "toLongOrNull",
        "toUpperCase",
        "is",
        "to",
        "toOrNull",
      ),
      "java.lang.Object" -> Set(
        "isBigDecimal",
        "toBigDecimal",
        "toBigDecimalOrNull",
        "isBoolean",
        "toBoolean",
        "toBooleanOrNull",
        "isDouble",
        "toDouble",
        "toDoubleOrNull",
        "isLong",
        "toLong",
        "toLongOrNull",
        "toString",
        "is",
        "to",
        "toOrNull",
        "isMap",
        "toMap",
        "toMapOrNull",
        "isList",
        "toList",
        "toListOrNull"
      ),
    )
  }

}

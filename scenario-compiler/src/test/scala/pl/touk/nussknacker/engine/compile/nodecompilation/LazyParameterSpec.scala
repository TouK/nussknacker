package pl.touk.nussknacker.engine.compile.nodecompilation

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.LazyParameter.CustomLazyParameter
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed

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

    val result = tupled.evaluate(Context(""))

    result shouldEqual (123, "foo")
  }

  private def checkParameterInvokedOnceAfterTransform(
      transform: LazyParameter[Integer] => LazyParameter[_ <: AnyRef]
  ) = {

    var invoked = 0
    val evalParameter = new CustomLazyParameter[Integer] {
      override def returnType: typing.TypingResult = Typed[Integer]
      override def evaluate: Context => Integer = {
        invoked += 1
        _ => 123
      }
    }

    val mappedParam = transform(evalParameter)
    val fun         = mappedParam.evaluate
    fun(Context(""))
    fun(Context(""))

    invoked shouldEqual 1
  }

}

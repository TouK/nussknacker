package pl.touk.nussknacker.engine.management.sample.global

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.{Documentation, GenericType, TypingFunction}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}

object GenericHelperFunction {
  private class HelperFun1 extends TypingFunction {
    private val IntOK = "OK: Int"
    private val StringOK = "OK: String"

    override def expectedParameters(): List[(String, TypingResult)] =
      List(("example of desired type", Typed(Typed[Int], Typed[String])))

    override def expectedResult(): TypingResult =
      Typed(Typed.fromInstance(IntOK), Typed.fromInstance(StringOK))

    override def apply(arguments: List[TypingResult]): ValidatedNel[String, TypingResult] = arguments match {
      case x :: Nil if x.canBeSubclassOf(Typed[Int]) => Typed.fromInstance(IntOK).validNel
      case x :: Nil if x.canBeSubclassOf(Typed[String]) => Typed.fromInstance(StringOK).validNel
      case _ => "Error message".invalidNel
    }

    def applyValue(arguments: List[Any]): Any = arguments match {
      case (_: Int) :: Nil =>
      case (_: String) :: Nil => "OK: String"
      case _ => throw new AssertionError("method called with argument that should cause validation error")
    }
  }

  @Documentation(description = "myFunction is a generic function")
  @GenericType(typingFunction = classOf[HelperFun1])
  def Fun1(arguments: List[Any]): Any = (new HelperFun1).applyValue(arguments)


  private class HelperFun2 extends TypingFunction {
    override def expectedParameters(): List[(String, TypingResult)] = List(("list", Typed[List[Object]]))

    override def expectedResult(): TypingResult = Unknown

    private val listClass = classOf[List[_]]

    override def apply(arguments: List[TypingResult]): ValidatedNel[String, TypingResult] = arguments match {
      case TypedClass(`listClass`, t :: Nil) :: Nil => t.validNel
      case TypedClass(`listClass`, _) :: Nil => "List must have one parameter".invalidNel
      case _ :: Nil => "Expected typed class".invalidNel
      case _ => "Expected one argument".invalidNel
    }
  }
}

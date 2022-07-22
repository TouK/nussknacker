package pl.touk.nussknacker.engine.management.sample.global

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.{Documentation, GenericType, TypingFunction}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}

import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

object GenericHelperFunction {
  @Documentation(description = "myFunction is a generic function")
  @GenericType(typingFunction = classOf[ExtractTypeHelper])
  def extractType(arguments: List[Any]): Any = (new ExtractTypeHelper).applyValue(arguments)

  private class ExtractTypeHelper extends TypingFunction {
    private val IntOK = "OK: Int"
    private val StringOK = "OK: String"

    override def staticParameters(): List[(String, TypingResult)] =
      List(("example of desired type", Typed(Typed[Int], Typed[String])))

    override def staticResult(): TypingResult =
      Typed(Typed.fromInstance(IntOK), Typed.fromInstance(StringOK))

    override def apply(arguments: List[TypingResult]): ValidatedNel[String, TypingResult] = arguments match {
      case x :: Nil if x.canBeSubclassOf(Typed[Int]) => Typed.fromInstance(IntOK).validNel
      case x :: Nil if x.canBeSubclassOf(Typed[String]) => Typed.fromInstance(StringOK).validNel
      case _ => "Error message".invalidNel
    }

    def applyValue(arguments: List[Any]): Any = arguments match {
      case (_: Int) :: Nil => IntOK
      case (_: String) :: Nil => StringOK
      case _ => throw new AssertionError("method called with argument that should cause validation error")
    }
  }


  @Documentation(description = "other generic function")
  @GenericType(typingFunction = classOf[HeadHelper])
  def head(arguments: java.util.List[Any]): Any = arguments.asScala match {
    case x :: _ => x
    case _ => throw new AssertionError("method called with argument that should cause validation error")
  }

  private class HeadHelper extends TypingFunction {
    private val listClass = classOf[java.util.List[_]]
    private val listType = Typed.typedClass(listClass, List(Unknown))

    override def staticParameters(): List[(String, TypingResult)] =
      List(("list", listType))

    override def staticResult(): TypingResult = Unknown

    override def apply(arguments: List[TypingResult]): ValidatedNel[String, TypingResult] = arguments match {
      case TypedClass(`listClass`, t :: Nil) :: Nil => t.validNel
      case TypedClass(`listClass`, _) :: Nil => throw new AssertionError("Lists must have one parameter")
      case _ :: Nil => "Expected list".invalidNel
      case _ => "Expected one argument".invalidNel
    }
  }

  @Documentation(description = "other generic function")
  @GenericType(typingFunction = classOf[ZipHelper])
  def zip(arguments: java.util.List[AnyRef]): Map[String, AnyRef] = arguments.asScala match {
    case lst if lst.nonEmpty => lst.zipWithIndex.map{ case (v, i) => i.toString -> v }.toMap
    case _ => throw new AssertionError("method called with argument that should cause validation error")
  }

  private class ZipHelper extends TypingFunction {

    override def staticParameters(): List[(String, TypingResult)] =
      List(("list", Unknown))

    override def staticResult(): TypingResult = Unknown

    override def apply(arguments: List[TypingResult]): ValidatedNel[String, TypingResult] = arguments match {
      case lst if lst.nonEmpty =>
        TypedObjectTypingResult(lst.zipWithIndex.map{ case(v, i) => i.toString -> v }).validNel
      case _ => "Expected at least argument".invalidNel
    }
  }
}

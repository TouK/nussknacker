package pl.touk.nussknacker.engine.management.sample.global

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, ExpressionParseError, GenericFunctionError, GenericType, Signature, TypingFunction}
import pl.touk.nussknacker.engine.api.Documentation
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypedObjectWithValue, TypingResult, Unknown}

import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

object GenericHelperFunction {
  @Documentation(description = "returns first element of list")
  @GenericType(typingFunction = classOf[HeadHelper])
  def head[T >: Null](list: java.util.List[T]): T =
    list.asScala.headOption.orNull

  private class HeadHelper extends TypingFunction {
    private val listClass = classOf[java.util.List[_]]

    private def error(arguments: List[TypingResult]) = {
      new ArgumentTypeError(
        new Signature("head", arguments, None),
        List(new Signature("head", List(Typed.fromDetailedType[java.util.List[Object]]), None))
      )
    }

    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = arguments match {
      case TypedClass(`listClass`, t :: Nil) :: Nil => t.validNel
      case TypedClass(`listClass`, _) :: Nil => throw new AssertionError("Lists must have one parameter")
      case _ => error(arguments).invalidNel
    }
  }


  @Documentation(description = "returns example of given type")
  @GenericType(typingFunction = classOf[ExampleOfTypeHelper])
  def exampleOfType(typeName: String): Object = ???

  private class ExampleOfTypeHelper extends TypingFunction {
    private val stringClass = classOf[String]
    private val expectedArgument = Typed(Typed.fromInstance("String"), Typed.fromInstance("Double"), Typed.fromInstance("Int"))
    private val expectedResult = Typed(Typed[String], Typed[Double], Typed[Int])

    private def error(arguments: List[TypingResult]) = {
      new ArgumentTypeError(
        new Signature("exampleOfType", arguments, None),
        List(new Signature("exampleOfType", List(expectedArgument), None))
      )
    }

    override def staticParameters(): Option[List[(String, TypingResult)]] =
      Some(List(("typeName", expectedArgument)))

    override def staticResult(): Option[TypingResult] =
      Some(expectedResult)

    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = arguments match {
      case TypedObjectWithValue(TypedClass(`stringClass`, Nil), typ: String) :: Nil => typ match {
        case "String" => Typed[String].validNel
        case "Int" => Typed[Int].validNel
        case "Double" => Typed[Double].validNel
        case _ => new GenericFunctionError("Expected string with value 'String', 'Int' or 'Double'").invalidNel
      }
      case a@TypedObjectWithValue(TypedClass(`stringClass`, _), _) :: Nil =>
        throw new AssertionError(s"Found illegal type $a")
      case TypedClass(`stringClass`, Nil) :: Nil =>
        new GenericFunctionError("Expected string with known value").invalidNel
      case a@TypedClass(`stringClass`, _) :: Nil =>
        throw new AssertionError(s"Found illegal type $a")
      case _ =>
        error(arguments).invalidNel
    }
  }


  @Documentation(description = "fails to compile when given object without field 'a'")
  @GenericType(typingFunction = classOf[RequiresFiledAHelper])
  def getFieldA(obj: Any): Any = ???

  private class RequiresFiledAHelper extends TypingFunction {
    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = arguments match {
      case TypedObjectTypingResult(fields, _, _) :: Nil => fields.get("a") match {
        case Some(x) => x.validNel
        case None => new GenericFunctionError("Given object does not have field 'a'").invalidNel
      }
      case _ :: Nil =>
        new GenericFunctionError("Expected typed object").invalidNel
      case _ =>
        new GenericFunctionError("Expected one argument").invalidNel
    }
  }


  @Documentation(description = "adds field 'a' to given object")
  @GenericType(typingFunction = classOf[AddFieldAHelper])
  def addFieldA(obj: Any): Any = ???

  private class AddFieldAHelper extends TypingFunction {
    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = arguments match {
      case TypedObjectTypingResult(fields, obj, info) :: Nil => fields.get("a") match {
        case Some(_) => new GenericFunctionError("Given object already has field 'a'").invalidNel
        case None => TypedObjectTypingResult(fields + ("a" -> Typed[Int]), obj, info).validNel
      }
      case _ :: Nil =>
        new GenericFunctionError("Expected typed object").invalidNel
      case _ =>
        new GenericFunctionError("Expected one argument").invalidNel
    }
  }

  def generic(a: java.util.List[Boolean], x: Int*): Int = {
    a.asScala.count(x => x) + x.map(_ + 1).sum
  }
}

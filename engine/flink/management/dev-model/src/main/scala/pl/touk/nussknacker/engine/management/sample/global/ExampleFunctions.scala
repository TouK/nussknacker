package pl.touk.nussknacker.engine.management.sample.global

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.Documentation
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError.ArgumentTypeError
import pl.touk.nussknacker.engine.api.generics.{
  GenericFunctionTypingError,
  GenericType,
  MethodTypeInfo,
  Parameter,
  TypingFunction
}
import pl.touk.nussknacker.engine.api.typed.typing.{
  Typed,
  TypedClass,
  TypedObjectTypingResult,
  TypedObjectWithValue,
  TypingResult
}

import scala.annotation.varargs
import scala.jdk.CollectionConverters._

object ExampleFunctions {

  @Documentation(description = "returns first element of list")
  @GenericType(typingFunction = classOf[HeadHelper])
  def head[T >: Null](list: java.util.List[T]): T =
    list.asScala.headOption.orNull

  private class HeadHelper extends TypingFunction {
    private val listClass = classOf[java.util.List[_]]

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
      case TypedClass(`listClass`, t :: Nil) :: Nil => t.validNel
      case TypedClass(`listClass`, _) :: Nil        => throw new AssertionError("Lists must have one parameter")
      case _                                        => GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

  }

  @Documentation(description = "returns example of given type")
  @GenericType(typingFunction = classOf[ExampleOfTypeHelper])
  def exampleOfType(typeName: String): Object = ???

  private class ExampleOfTypeHelper extends TypingFunction {
    private val stringClass = classOf[String]
    private val expectedArgument =
      Typed(Typed.fromInstance("String"), Typed.fromInstance("Double"), Typed.fromInstance("Int"))
    private val expectedResult = Typed(Typed[String], Typed[Double], Typed[Int])

    override def signatures: Option[NonEmptyList[MethodTypeInfo]] =
      Some(NonEmptyList.one(MethodTypeInfo(List(Parameter("typeName", expectedArgument)), None, expectedResult)))

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
      case TypedObjectWithValue(TypedClass(`stringClass`, Nil), typ: String) :: Nil =>
        typ match {
          case "String" => Typed[String].validNel
          case "Int"    => Typed[Int].validNel
          case "Double" => Typed[Double].validNel
          case _        => GenericFunctionTypingError.ArgumentTypeError.invalidNel
        }
      case a @ TypedObjectWithValue(TypedClass(`stringClass`, _), _) :: Nil =>
        throw new AssertionError(s"Found illegal type $a")
      case TypedClass(`stringClass`, Nil) :: Nil =>
        GenericFunctionTypingError.OtherError("Expected string with known value").invalidNel
      case a @ TypedClass(`stringClass`, _) :: Nil =>
        throw new AssertionError(s"Found illegal type $a")
      case _ =>
        GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

  }

  @Documentation(description = "fails to compile when given object without field 'a'")
  @GenericType(typingFunction = classOf[RequiresFiledAHelper])
  def getFieldA(obj: Any): Any = ???

  private class RequiresFiledAHelper extends TypingFunction {

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
      case TypedObjectTypingResult(fields, _, _) :: Nil =>
        fields.get("a") match {
          case Some(x) => x.validNel
          case None    => GenericFunctionTypingError.OtherError("Given object does not have field 'a'").invalidNel
        }
      case _ =>
        GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

  }

  @Documentation(description = "adds field 'a' to given object")
  @GenericType(typingFunction = classOf[AddFieldAHelper])
  def addFieldA(obj: Any): Any = ???

  private class AddFieldAHelper extends TypingFunction {

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
      case TypedObjectTypingResult(fields, obj, info) :: Nil =>
        fields.get("a") match {
          case Some(_) => GenericFunctionTypingError.OtherError("Given object already has field 'a'").invalidNel
          case None    => Typed.record(fields + ("a" -> Typed[Int]), obj, info).validNel
        }
      case _ =>
        GenericFunctionTypingError.ArgumentTypeError.invalidNel
    }

  }

  @Documentation(description = "zips up to 3 numbers")
  @GenericType(typingFunction = classOf[ZipHelper])
  @varargs
  def zip(x: Number*): Any = ???

  private class ZipHelper extends TypingFunction {

    override def signatures: Option[NonEmptyList[MethodTypeInfo]] =
      Some(
        NonEmptyList.of(
          MethodTypeInfo(Parameter("arg", Typed[Number]) :: Nil, None, Typed[Number]),
          MethodTypeInfo(
            Parameter("left", Typed[Number]) :: Parameter("right", Typed[Number]) :: Nil,
            None,
            Typed[Number]
          ),
          MethodTypeInfo(
            Parameter("left", Typed[Number]) :: Parameter("mid", Typed[Number]) :: Parameter(
              "right",
              Typed[Number]
            ) :: Nil,
            None,
            Typed[Number]
          )
        )
      )

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = {
      if (arguments.exists(!_.canBeConvertedTo(Typed[Number]))) return ArgumentTypeError.invalidNel
      arguments match {
        case t :: Nil           => t.validNel
        case l :: r :: Nil      => Typed.record(Map("left" -> l, "right" -> r)).validNel
        case l :: m :: r :: Nil => Typed.record(Map("left" -> l, "mid" -> m, "right" -> r)).validNel
        case _                  => ArgumentTypeError.invalidNel
      }
    }

  }

}

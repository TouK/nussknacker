package pl.touk.nussknacker.engine.management.sample.global

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError.{ArgumentTypeError, OtherError}
import pl.touk.nussknacker.engine.api.generics._
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder
import pl.touk.nussknacker.engine.api.typed.typing._

import scala.annotation.varargs
import scala.jdk.CollectionConverters._

object DocumentationFunctions {

  @GenericType(typingFunction = classOf[HeadGenericFunction])
  def head[T](list: java.util.List[T]): T =
    list.get(0)

  private class HeadGenericFunction extends TypingFunction {
    private val listClass = classOf[java.util.List[_]]

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = {
      arguments match {
        case TypedClass(`listClass`, t :: Nil) :: Nil => t.validNel
        case _                                        => ArgumentTypeError.invalidNel
      }
    }

  }

  @GenericType(typingFunction = classOf[PlusGenericFunction])
  def plus(left: Any, right: Any): Any = (left, right) match {
    case (left: Int, right: Int)       => left + right
    case (left: Double, right: Double) => left + right
    case (left: String, right: String) => left + right
    case _                             => throw new AssertionError("should not be reached")
  }

  private class PlusGenericFunction() extends TypingFunction {
    private val numberType = Typed.typedClass[Number]
    private val intType    = Typed.typedClass[Int]
    private val doubleType = Typed.typedClass[Double]
    private val stringType = Typed.typedClass[String]

    override def signatures: Option[NonEmptyList[MethodTypeInfo]] = Some(
      NonEmptyList.of(
        MethodTypeInfo
          .withoutVarargs(Parameter("left", numberType) :: Parameter("right", numberType) :: Nil, numberType),
        MethodTypeInfo.withoutVarargs(
          Parameter("left", stringType) :: Parameter("right", stringType) :: Nil,
          stringType
        )
      )
    )

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
      case left :: right :: Nil =>
        (left.withoutValue, right.withoutValue) match {
          case (`intType`, `intType`)       => intType.validNel
          case (`doubleType`, `doubleType`) => doubleType.validNel
          case (l, r) if List(l, r).forall(_.canBeSubclassOf(numberType)) =>
            OtherError(s"Addition of ${l.display} and ${r.display} is not supported").invalidNel
          case (`stringType`, `stringType`) => stringType.validNel
          case _                            => ArgumentTypeError.invalidNel
        }
      case _ => ArgumentTypeError.invalidNel
    }

  }

  @GenericType(typingFunction = classOf[UnionGenericFunction])
  def union(x: java.util.Map[String, Any], y: java.util.Map[String, Any]): java.util.Map[String, Any] = {
    val res = new java.util.HashMap[String, Any](x)
    res.putAll(y)
    res
  }

  private class UnionGenericFunction extends TypingFunction {

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
      case TypedObjectTypingResult(x, _, infoX) :: TypedObjectTypingResult(y, _, infoY) :: Nil =>
        if (x.keys.exists(y.keys.toSet.contains))
          OtherError("Argument maps have common field").invalidNel
        else
          Typed.record(x ++ y, Typed.typedClass[java.util.HashMap[_, _]], infoX ++ infoY).validNel
      case _ =>
        ArgumentTypeError.invalidNel
    }

  }

  @GenericType(typingFunction = classOf[GetFieldGenericFunction])
  def getField(obj: java.util.Map[String, Any], name: String): Any =
    obj.get(name)

  private class GetFieldGenericFunction extends TypingFunction {
    private val stringType = Typed.typedClass[String]

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = arguments match {
      case TypedObjectTypingResult(fields, _, _) :: TypedObjectWithValue(`stringType`, name: String) :: Nil =>
        fields.get(name) match {
          case Some(v) => v.validNel
          case None    => OtherError("No field with given name").invalidNel
        }
      case TypedObjectTypingResult(_, _, _) :: x :: Nil if x.canBeSubclassOf(stringType) =>
        OtherError("Expected string with known value").invalidNel
      case _ =>
        ArgumentTypeError.invalidNel
    }

  }

  @GenericType(typingFunction = classOf[ToListGenericFunction])
  @varargs
  def toList[T](elems: T*): java.util.List[T] =
    elems.asJava

  private class ToListGenericFunction extends TypingFunction {

    override def computeResultType(
        arguments: List[TypingResult]
    ): ValidatedNel[GenericFunctionTypingError, TypingResult] = {
      val commonSupertype = arguments
        .reduceOption(CommonSupertypeFinder.Default.commonSupertype)
        .getOrElse(Unknown)
      Typed.genericTypeClass[java.util.List[_]](commonSupertype :: Nil).validNel
    }

  }

}

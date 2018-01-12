package pl.touk.nussknacker.engine.api.typed

import cats.kernel.CommutativeMonoid
import org.apache.commons.lang3.ClassUtils

import scala.reflect.ClassTag

object typing {

  sealed trait TypingResult {

    def canBeSubclassOf(clazzRef: ClazzRef)(implicit classLoader: ClassLoader) : Boolean

    def display: String

  }

  object TypedMapTypingResult {

    def apply(definition: TypedMapDefinition)(implicit classLoader: ClassLoader) : TypedMapTypingResult
      = TypedMapTypingResult(definition.fields.mapValues(Typed(_)))
  }

  case class TypedMapTypingResult(fields: Map[String, TypingResult]) extends TypingResult {

    override def canBeSubclassOf(clazzRef: ClazzRef)(implicit classLoader: ClassLoader): Boolean = Typed[java.util.Map[_, _]].canBeSubclassOf(clazzRef)

    override def display: String = s"map with fields:${fields.map { case (name, typ) => s"$name of type ${typ.display}"}.mkString(", ")}"
  }

  case object Unknown extends TypingResult {
    override def canBeSubclassOf(clazzRef: ClazzRef)(implicit classLoader: ClassLoader): Boolean = true

    override val display = "unknown"
  }

  case class Typed(possibleTypes: Set[TypedClass]) extends TypingResult {
    override def canBeSubclassOf(clazzRef: ClazzRef)(implicit classLoader: ClassLoader): Boolean = {
      val typed = TypedClass(clazzRef)
      possibleTypes.exists(pt => ClassUtils.isAssignable(pt.klass, typed.klass))
    }

    override val display : String = possibleTypes.toList match {
      case Nil => "empty"
      case h::Nil => s"type '${printClass(h)}'"
      case many => many.map(printClass).mkString("one of (", ", ", ")")
    }

    //TODO: should we use simple name here?
    private def printClass(h: TypedClass) = h.klass.getName

  }

  case class TypedClass(klass: Class[_])

  object TypedClass {
    def apply(klass: ClazzRef)(implicit classLoader: ClassLoader) : TypedClass =
      TypedClass(klass.toClass(classLoader))
  }

  object Typed {

    def apply[T:ClassTag] : TypingResult = Typed(Set(TypedClass(implicitly[ClassTag[T]].runtimeClass)))

    def apply(klass: Class[_]) : TypingResult = Typed(Set(TypedClass(klass)))

    def apply(klass: ClazzRef)(implicit classLoader: ClassLoader) : TypingResult = {
      if (klass == ClazzRef.unknown) {
        Unknown
      } else {
        Typed(Set(TypedClass(klass)))
      }
    }

  }

  implicit val commutativeMonoid: CommutativeMonoid[TypingResult] = new CommutativeMonoid[TypingResult] {

    //ha... is it really this way? :)
    override def empty: TypingResult = Typed[Any]

    override def combine(x: TypingResult, y: TypingResult): TypingResult = (x, y) match {
      case (Unknown, _) => Unknown
      case (_, Unknown) => Unknown
      case (Typed(set1), Typed(set2)) => Typed(set1 ++ set2)
      case _ => throw new IllegalArgumentException("NOT IMPLEMENTED YET :)")
    }

  }
}

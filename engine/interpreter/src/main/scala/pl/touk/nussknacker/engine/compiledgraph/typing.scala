package pl.touk.nussknacker.engine.compiledgraph

import cats.kernel.CommutativeMonoid
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ClazzRef

import scala.reflect.ClassTag

object typing {

  sealed trait TypingResult {

    def canBeSubclassOf(clazzRef: ClazzRef)(implicit classLoader: ClassLoader) : Boolean

    def display: String

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
      case h::Nil => s"type '${h.klass.getName}'"
      case many => many.map(k => ClazzRef(k.klass)).mkString("one of (", ", ", ")")
    }

  }

  case class TypedClass(klass: Class[_])

  object TypedClass {
    def apply(klass: ClazzRef)(implicit classLoader: ClassLoader) : TypedClass =
      TypedClass(klass.toClass(classLoader))
  }

  object Typed {

    val any: TypingResult = Typed[Any]

    def apply[T:ClassTag] : TypingResult = Typed(Set(TypedClass(implicitly[ClassTag[T]].runtimeClass)))

    def apply(klass: ClazzRef)(implicit classLoader: ClassLoader) : TypingResult = {
      val typed = Typed(Set(TypedClass(klass)))
      if (typed == Typed.any) Unknown else typed
    }

  }

  implicit val commutativeMonoid: CommutativeMonoid[TypingResult] = new CommutativeMonoid[TypingResult] {

    //ha... is it really this way? :)
    override def empty: TypingResult = Typed[Any]

    override def combine(x: TypingResult, y: TypingResult): TypingResult = (x, y) match {
      case (Unknown, _) => Unknown
      case (_, Unknown) => Unknown
      case (Typed(set1), Typed(set2)) => Typed(set1 ++ set2)
    }

  }
}

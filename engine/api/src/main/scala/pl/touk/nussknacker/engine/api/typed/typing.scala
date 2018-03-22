package pl.touk.nussknacker.engine.api.typed

import cats.kernel.CommutativeMonoid
import org.apache.commons.lang3.ClassUtils

import scala.reflect.ClassTag

object typing {

  sealed trait TypingResult {

    def canBeSubclassOf(clazzRef: ClazzRef) : Boolean

    def display: String

  }

  object TypedMapTypingResult {

    def apply(definition: TypedMapDefinition): TypedMapTypingResult
      = TypedMapTypingResult(definition.fields.mapValues(Typed(_)))
  }

  case class TypedMapTypingResult(fields: Map[String, TypingResult]) extends TypingResult {

    override def canBeSubclassOf(clazzRef: ClazzRef): Boolean = Typed[java.util.Map[_, _]].canBeSubclassOf(clazzRef)

    override def display: String = s"map with fields:${fields.map { case (name, typ) => s"$name of type ${typ.display}"}.mkString(", ")}"
  }

  case object Unknown extends TypingResult {
    override def canBeSubclassOf(clazzRef: ClazzRef): Boolean = true

    override val display = "unknown"
  }

  case class Typed(possibleTypes: Set[TypedClass]) extends TypingResult {
    override def canBeSubclassOf(clazzRef: ClazzRef): Boolean = {
      possibleTypes.exists(_.canBeSubclassOf(clazzRef))
    }

    override val display : String = possibleTypes.toList match {
      case Nil => "empty"
      case h::Nil => s"type '${printClass(h)}'"
      case many => many.map(printClass).mkString("one of (", ", ", ")")
    }

    //TODO: should we use simple name here?
    private def printClass(h: TypedClass) = h.klass.getName

  }

  //TODO: in near future should be replaced by ClazzRef!
  case class TypedClass(klass: Class[_], params: List[TypingResult]) {
    //TOOD: params?
    def canBeSubclassOf(clazzRef: ClazzRef): Boolean = ClassUtils.isAssignable(klass, clazzRef.clazz)
  }

  object TypedClass {
    def apply(klass: ClazzRef) : TypedClass =
      TypedClass(klass.clazz, klass.params.map(Typed.apply))

    def apply[T:ClassTag] : TypedClass = TypedClass(ClazzRef[T].clazz, List())

  }

  object Typed {

    def apply[T:ClassTag] : TypingResult = apply(ClazzRef[T])

    def apply(klass: ClazzRef) : TypingResult = {
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

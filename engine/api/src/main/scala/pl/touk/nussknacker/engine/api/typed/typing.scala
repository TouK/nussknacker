package pl.touk.nussknacker.engine.api.typed

import java.util

import cats.kernel.CommutativeMonoid
import org.apache.commons.lang3.ClassUtils

import scala.reflect.ClassTag

object typing {

  sealed trait TypingResult {

    def canHaveAnyPropertyOrField: Boolean

    def canBeSubclassOf(clazzRef: ClazzRef) : Boolean

    def display: String

  }

  object TypedObjectTypingResult {

    def apply(definition: TypedObjectDefinition): TypedObjectTypingResult =
    //we don't use mapValues here to avoid lazy evaluation that crashes during serialization...
      TypedObjectTypingResult(definition.fields.map { case (k, v) => (k, Typed(v))})

    def apply(fields: Map[String, TypingResult]): TypedObjectTypingResult =
      TypedObjectTypingResult(fields, TypedClass[java.util.Map[_, _]])

  }

  case class TypedObjectTypingResult(fields: Map[String, TypingResult], objType: TypedClass) extends TypingResult {

    override def canBeSubclassOf(clazzRef: ClazzRef): Boolean = objType.canBeSubclassOf(clazzRef)

    override def canHaveAnyPropertyOrField: Boolean = false

    override def display: String = s"object with fields:${fields.map { case (name, typ) => s"$name of type ${typ.display}"}.mkString(", ")}"

  }

  case object Unknown extends TypingResult {
    override def canBeSubclassOf(clazzRef: ClazzRef): Boolean = true

    override def canHaveAnyPropertyOrField: Boolean = true

    override val display = "unknown"
  }

  case class Typed(possibleTypes: Set[TypedClass]) extends TypingResult {
    override def canBeSubclassOf(clazzRef: ClazzRef): Boolean = {
      possibleTypes.exists(_.canBeSubclassOf(clazzRef))
    }

    override def canHaveAnyPropertyOrField: Boolean = {
      canBeSubclassOf(ClazzRef[util.Map[_, _]]) ||
        // mainly for avro's GenericRecord purpose
        hasGetFieldByNameMethod(possibleTypes)
    }

    private def hasGetFieldByNameMethod(possibleTypes: Set[TypedClass]) = {
      possibleTypes.exists(tc => tc.klass.getMethods.exists(m => m.getName == "get" && (m.getParameterTypes sameElements Array(classOf[String]))))
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
      case (Unknown, typed) => typed
      case (typed, Unknown) => typed
      case (Typed(set1), Typed(set2)) => Typed(set1 ++ set2)
      case _ => throw new IllegalArgumentException("NOT IMPLEMENTED YET :)")
    }

  }
}

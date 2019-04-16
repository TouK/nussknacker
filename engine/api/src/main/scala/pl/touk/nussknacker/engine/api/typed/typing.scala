package pl.touk.nussknacker.engine.api.typed

import java.util

import cats.kernel.CommutativeMonoid
import org.apache.commons.lang3.ClassUtils

import scala.reflect.ClassTag

object typing {

  sealed trait TypingResult {

    def canHaveAnyPropertyOrField: Boolean

    def canBeSubclassOf(typingResult: TypingResult) : Boolean

    def display: String

    def objType: TypedClass

  }

  object TypedObjectTypingResult {

    def apply(definition: TypedObjectDefinition): TypedObjectTypingResult =
    //we don't use mapValues here to avoid lazy evaluation that crashes during serialization...
      TypedObjectTypingResult(definition.fields.map { case (k, v) => (k, Typed(v))})

    def apply(fields: Map[String, TypingResult]): TypedObjectTypingResult =
      TypedObjectTypingResult(fields, TypedClass[java.util.Map[_, _]])

  }

  case class TypedObjectTypingResult(fields: Map[String, TypingResult], objType: TypedClass) extends TypingResult {

    override def canBeSubclassOf(typingResult: TypingResult): Boolean = typingResult match {
      case TypedObjectTypingResult(otherFields, _) =>
        objType.canBeSubclassOf(typingResult) && otherFields.forall {
          case (name, typ) => fields.get(name).exists(_.canBeSubclassOf(typ))
        }
      case _ =>
        objType.canBeSubclassOf(typingResult)
    }

    override def canHaveAnyPropertyOrField: Boolean = false

    override def display: String = s"object with fields:${fields.map { case (name, typ) => s"$name of type ${typ.display}"}.mkString(", ")}"

  }

  case object Unknown extends TypingResult {
    override def canBeSubclassOf(typingResult: TypingResult): Boolean = true

    override def canHaveAnyPropertyOrField: Boolean = true

    override val display = "unknown"

    override def objType: TypedClass = TypedClass[Any]
  }

  case class Typed(possibleTypes: Set[TypedClass]) extends TypingResult {
    override def canBeSubclassOf(typingResult: TypingResult): Boolean = {
      possibleTypes.exists(_.canBeSubclassOf(typingResult))
    }

    override def canHaveAnyPropertyOrField: Boolean = {
      canBeSubclassOf(Typed[util.Map[_, _]]) ||
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

    override def objType: TypedClass = if (possibleTypes.size == 1) possibleTypes.head else TypedClass[Any]
  }

  //TODO: make sure parameter list has right size - can be filled with Unknown if needed
  case class TypedClass(klass: Class[_], params: List[TypingResult]) {
    def canBeSubclassOf(typingResult: TypingResult): Boolean = {
      val otherTyped = typingResult.objType
      this == otherTyped || ClassUtils.isAssignable(klass, otherTyped.klass) && (typingResult.canHaveAnyPropertyOrField ||
        //we are lax here - the generic type may be co- or contra-variant - and we don't want to
        //throw validation errors in this case. It's better to accept to much than too little
        otherTyped.params.zip(params).forall(t => t._1.canBeSubclassOf(t._2) || t._2.canBeSubclassOf(t._1)))
    }
  }

  object TypedClass {
    def apply(klass: ClazzRef) : TypedClass =
      TypedClass(klass.clazz, klass.params.map(Typed.apply))

    def apply[T:ClassTag] : TypedClass = TypedClass(ClazzRef[T].clazz, List())

  }

  object Typed {

    def apply[T:ClassTag] : TypingResult = apply(ClazzRef[T])

    def apply(klass: Class[_]): TypingResult = apply(ClazzRef(klass))

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

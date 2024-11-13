package pl.touk.nussknacker.engine.flink.api

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedTaggedValue, TypingResult}

import java.util

// This is not a pretty solution - we use tagged types to add extra hints about serialization. It is needed because
// Flink doesn't allow to pass underlying object of multiset (Map[String, Int]) into multiset.
// Because of that we have to provide a hint to TypingResultAwareTypeInformationDetection that it should
// pick MultisetTypeInfo instead of MapTypeInfo for a given Map.
// Other options that we have:
// - Merge type alignment step with type info detection stage
// - Pass this hints in some  similar structure next to current type info
// - Make current hints used in records (TypedObjectTypingResult.additionalInfo) available for all types
object TypedMultiset {

  private val multisetTag = "multiset"

  private val javaMapClass = classOf[util.Map[_, _]]

  def apply(elementType: TypingResult): TypingResult = {
    new TypedTaggedValue(Typed.genericTypeClass[java.util.Map[_, _]](List(elementType, Typed[Int])), multisetTag) {
      // It is not a good idea, to inherit from case class. It is temporary solution before we figure out something better
      override def display: String = s"Multiset[${elementType.display}]"
    }
  }

  def unapply(typingResult: TypingResult): Option[TypingResult] = Option(typingResult).collect {
    case TypedTaggedValue(TypedClass(klass, keyType :: valueType :: Nil, _), `multisetTag`)
        if klass == javaMapClass && valueType == Typed[Int] =>
      keyType
  }

}

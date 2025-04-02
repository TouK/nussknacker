package pl.touk.nussknacker.engine.schemedkafka.typed

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedJson, TypedObjectTypingResult, TypingResult, Unknown}

object TypingResultFromJsonSampleTypeDeterminer {

  private val typeClassListUnknown = Typed.genericTypeClass[java.util.List[_]](List(Unknown))

  def apply(typingResult: TypingResult): TypingResult = {
    // This is some kind of heuristic; when the user passes '{}', '[]' or 'null' we are not sure what will be in the fields
    // empty record does not provide any value for the users; they cannot access any fields. TypedJson, on the other hand, allows a dynamic navigation
    typingResult.withoutValue match {
      case obj: TypedObjectTypingResult if obj.fields.isEmpty => TypedJson
      case Unknown                                            => TypedJson
      case `typeClassListUnknown`                             => TypedJson
      case other                                              => other
    }
  }

}

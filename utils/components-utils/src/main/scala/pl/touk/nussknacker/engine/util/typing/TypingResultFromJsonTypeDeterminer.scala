package pl.touk.nussknacker.engine.util.typing

import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult, Unknown}

object TypingResultFromJsonTypeDeterminer {

  def apply(typingResult: TypingResult): TypingResult = {
    typingResult.withoutValue match {
      // This is some kind of heuristic; when the user passes '{}', we are not sure what will be in the fields
      // empty record does not provide any value for the users; they cannot access any fields. Unknown, on the other hand, allows a dynamic navigation
      case obj: TypedObjectTypingResult if obj.fields.isEmpty => Unknown
      case other                                              => other
    }
  }

}

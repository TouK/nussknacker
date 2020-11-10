package pl.touk.nussknacker.engine.api.typed

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{AdditionalDataValue, Typed, TypedObjectTypingResult, TypedUnion, Unknown}

class TypingResultDecoderSpec extends FunSuite with Matchers {

  test("should decode same type after encoding") {
    val decoder = new TypingResultDecoder(getClass.getClassLoader.loadClass)
    List(
      Unknown,
      Typed.fromDetailedType[List[String]],
      Typed.fromDetailedType[Map[String, AnyRef]],
      Typed.tagged(Typed.typedClass[String], "alamakota"),
      Typed.taggedDictValue(Typed.typedClass[String], "alamakota"),
      TypedUnion(Set(Typed.typedClass[String], Typed.typedClass[java.lang.Long])),
      //this wont' work, handling primitives should be done with more sophisticated classloading
      //Typed[Long]
      TypedObjectTypingResult(Map("field1" -> Typed[String], "field2" -> Unknown)),
      TypedObjectTypingResult(Map("field1" -> Typed[String]), Typed.typedClass[Map[String, Any]],
        Some(Map[String, AdditionalDataValue]("ad1" -> "aaa", "ad2" -> 22L, "ad3" -> true)))
    ).foreach { typing =>
      decoder.decodeTypingResults.decodeJson(TypeEncoders.typingResultEncoder(typing)) shouldBe Right(typing)
    }

  }


}

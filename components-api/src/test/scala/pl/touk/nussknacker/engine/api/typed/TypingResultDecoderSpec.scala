package pl.touk.nussknacker.engine.api.typed

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing._

class TypingResultDecoderSpec extends AnyFunSuite with Matchers {

  test("should decode same type after encoding") {
    val decoder = new TypingResultDecoder(getClass.getClassLoader.loadClass)
    List(
      Unknown,
      TypedNull,
      Typed.fromDetailedType[List[String]],
      Typed.fromDetailedType[Map[String, AnyRef]],
      Typed.tagged(Typed.typedClass[String], "alamakota"),
      TypedObjectWithValue(Typed.typedClass[String], "t"),
      TypedObjectWithValue(Typed.typedClass[Int], 789),
      TypedObjectWithValue(Typed.typedClass[Long], 15L),
      TypedObjectWithValue(Typed.typedClass[Float], 1.57f),
      TypedObjectWithValue(Typed.typedClass[Double], 23.547d),
      TypedObjectWithValue(Typed.typedClass[Boolean], false),
      Typed.fromInstance(Float.NaN),
      Typed.taggedDictValue(Typed.typedClass[String], "alamakota"),
      Typed(Typed.typedClass[String], Typed.typedClass[java.lang.Long]),
      // this wont' work, handling primitives should be done with more sophisticated classloading
      // Typed[Long]
      TypedObjectTypingResult(Map("field1" -> Typed[String], "field2" -> Unknown)),
      TypedObjectTypingResult(
        Map("field1" -> Typed[String]),
        Typed.typedClass[Map[String, Any]],
        Map[String, AdditionalDataValue]("ad1" -> "aaa", "ad2" -> 22L, "ad3" -> true)
      )
    ).foreach { typing =>
      decoder.decodeTypingResults.decodeJson(TypeEncoders.typingResultEncoder(typing)) shouldBe Right(typing)
    }

  }

}

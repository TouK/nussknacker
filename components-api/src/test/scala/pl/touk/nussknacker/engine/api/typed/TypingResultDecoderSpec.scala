package pl.touk.nussknacker.engine.api.typed

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.typed.typing.Typed.typedListWithElementValues
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import java.time.LocalDateTime
import scala.jdk.CollectionConverters._

class TypingResultDecoderSpec
    extends AnyFunSuite
    with ScalaCheckDrivenPropertyChecks
    with EitherValuesDetailedMessage
    with Matchers
    with LazyLogging {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 1000, minSize = 0)

  test("should decode same type after encoding") {
    val decoder = new TypingResultDecoder(getClass.getClassLoader.loadClass)
    List(
      Unknown,
      TypedNull,
      Typed.fromDetailedType[List[String]],
      Typed.fromDetailedType[Array[String]],
      Typed.fromDetailedType[Map[String, AnyRef]],
      Typed.tagged(Typed.typedClass[String], "alamakota"),
      TypedObjectWithValue(Typed.typedClass[String], "t"),
      TypedObjectWithValue(Typed.typedClass[Int], 789),
      TypedObjectWithValue(Typed.typedClass[Long], 15L),
      TypedObjectWithValue(Typed.typedClass[Float], 1.57f),
      TypedObjectWithValue(Typed.typedClass[Double], 23.547d),
      TypedObjectWithValue(Typed.typedClass[Boolean], false),
      Typed.fromInstance(Float.NaN),
      Typed.fromInstance(LocalDateTime.MAX),
      Typed.taggedDictValue(Typed.typedClass[String], "alamakota"),
      Typed(Typed.typedClass[String], Typed.typedClass[java.lang.Long]),
      // this wont' work, handling primitives should be done with more sophisticated classloading
      // Typed[Long]
      Typed.record(Map("field1" -> Typed[String], "field2" -> Unknown)),
      Typed.record(
        Map("field1" -> Typed[String]),
        Typed.typedClass[Map[String, Any]],
        Map[String, AdditionalDataValue]("ad1" -> "aaa", "ad2" -> 22L, "ad3" -> true)
      ),
      typedListWithElementValues(Typed[Int], List(1, 2, 3).asJava),
      typedListWithElementValues(Typed[String], List("foo", "bar").asJava),
      typedListWithElementValues(Typed.record(Map.empty), List(Map.empty.asJava).asJava),
      typedListWithElementValues(
        Typed.record(
          Map("a" -> TypedObjectWithValue(Typed.typedClass[Int], 1))
        ),
        List(Map("a" -> 1).asJava).asJava
      ),
      typedListWithElementValues(
        Typed.record(
          List(
            "a" -> Typed.typedClass[Int],
            "b" -> Typed.typedClass[Int]
          )
        ),
        List(Map("a" -> 1).asJava, Map("b" -> 2).asJava).asJava
      ),
      typedListWithElementValues(
        Unknown,
        List(Map("a" -> 1).asJava, 2).asJava
      ),
    ).foreach { typing =>
      val encoded = TypeEncoders.typingResultEncoder(typing)

      decoder.decodeTypingResults.decodeJson(encoded).rightValue shouldBe typing
    }

  }

  test("generator based encode decode round-trip") {
    val decoder = new TypingResultDecoder(getClass.getClassLoader.loadClass)
    // FIXME: tagged types doesn't work correctly
    forAll(TypingResultGen.typingResultGen(EnabledTypedFeatures(taggedTypes = false))) { input =>
      logger.trace(s"Checking: ${input.display}")
      withClue(s"Input: ${input.display};") {
        val encoded = TypeEncoders.typingResultEncoder(input)
        val decoded = decoder.decodeTypingResults.decodeJson(encoded).rightValue
        withClue(s"Decoded: ${decoded.display};") {
          decoded shouldBe input
        }
      }
    }
  }

}

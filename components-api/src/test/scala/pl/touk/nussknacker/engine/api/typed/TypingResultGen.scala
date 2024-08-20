package pl.touk.nussknacker.engine.api.typed

import org.scalacheck.{Arbitrary, Gen}
import pl.touk.nussknacker.engine.api.typed.TypingResultGen._
import pl.touk.nussknacker.engine.api.typed.typing._

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period}
import java.util.Collections
import scala.jdk.CollectionConverters._

class TypingResultGen private (features: EnabledTypedFeatures) {

  private lazy val nullGen = Gen.const(null)

  private lazy val numberGen: Gen[Number] = Gen.oneOf(
    Gen.const[java.lang.Byte](1.toByte),
    Gen.const[java.lang.Short](2.toShort),
    Gen.const[java.lang.Integer](3),
    Gen.const[java.lang.Long](4),
    Gen.const[java.math.BigInteger](java.math.BigInteger.valueOf(5)),
    Gen.const[java.lang.Float](6.7f),
    Gen.const[java.lang.Double](7.8),
    Gen.const[java.math.BigDecimal](new java.math.BigDecimal("8.9"))
  )

  private lazy val booleanGen = Arbitrary.arbitrary[Boolean]

  private lazy val stringGen = Gen.const("foo")

  private lazy val timeGen = Gen.oneOf(
    Gen.const(LocalDateTime.now()),
    Gen.const(LocalDate.now()),
    Gen.const(LocalTime.now()),
    Gen.const(Duration.ofDays(1)),
    Gen.const(Period.ofDays(2)),
  )

  private lazy val simpleTypeGen = Gen.oneOf(nullGen, numberGen, booleanGen, stringGen, timeGen)

  private lazy val listGen = valueGen.map(Collections.singletonList)

  private lazy val emptyMapGen = Gen.const(Collections.emptyMap[String, Any])

  private lazy val oneFieldMapGen = valueGen.map(v => Collections.singletonMap("f1", v))

  private lazy val combinationOfFieldMapGen =
    valueGen.flatMap(v1 => valueGen.map((v1, _))).map { case (v1, v2) => Map("f1" -> v1, "f2" -> v2).asJava }

  private lazy val mapGen = Gen.oneOf(emptyMapGen, oneFieldMapGen, combinationOfFieldMapGen)

  lazy val valueGen: Gen[Any] = Gen.lzy(Gen.oneOf(simpleTypeGen, listGen, mapGen))

  private lazy val typingResultWithValueGen = valueGen.map(Typed.fromInstance)

  private lazy val typingResultWithoutValueGen = typingResultWithValueGen.map(_.withoutValue)

  private lazy val typedClassFromStandardTypeGen = toSingleTypingResult(typingResultWithValueGen).map(_.objType)

  private lazy val typedFromCustomClass = Gen.oneOf(
    List(
      Typed.typedClass[Animal],
      Typed.typedClass[Pet],
      Typed.typedClass[Dog],
      Typed.typedClass[Cat],
      Typed.typedClass[Plant],
      Typed.typedClass[Cactus]
    )
  )

  private lazy val typedClassGen: Gen[typing.TypedClass] =
    Gen.oneOf(typedClassFromStandardTypeGen, typedFromCustomClass)

  private lazy val typedRecordSingleFieldGen = typingResultGen.map(t1 => Typed.record(Map("ff1" -> t1)))

  private lazy val typedRecordCombinationOfFieldGen =
    typingResultGen.flatMap(t1 => typingResultGen.map(t2 => Typed.record(Map("ff1" -> t1, "ff2" -> t2))))

  private lazy val typedRecordGen: Gen[TypedObjectTypingResult] =
    Gen.oneOf(typedRecordSingleFieldGen, typedRecordCombinationOfFieldGen)

  private lazy val typedListGen: Gen[typing.TypedClass] =
    typingResultGen.map(t => Typed.genericTypeClass[java.util.List[_]](List(t)))

  private lazy val typedArrayGen: Gen[typing.TypedClass] =
    Gen.oneOf(
      Typed.genericTypeClass[Array[Integer]](List(Typed[Integer])),
      Typed.genericTypeClass[Array[String]](List(Typed[String])),
      Typed.genericTypeClass[Array[AnyRef]](List(Unknown)),
    )

  private lazy val singleTypingResultGen: Gen[SingleTypingResult] = {
    // Frequency is taken empyrical, we are less interested in some cases
    Gen.frequency(
      5    -> typedClassGen ::
        5  -> toSingleTypingResult(typingResultWithoutValueGen) ::
        5  -> toSingleTypingResult(typingResultWithValueGen) ::
        10 -> typedRecordGen ::
        10 -> typedListGen ::
        1  -> typedArrayGen ::
        optionalGenWithFrequency(features.taggedTypes, 1 -> taggedTypeGen) :::
        1 -> taggedDictGen ::
        Nil: _*
    )
  }

  private lazy val taggedTypeGen = Gen.lzy(singleTypingResultGen.map(Typed.tagged(_, "tag1")))

  private lazy val taggedDictGen = Gen.lzy(singleTypingResultGen.map(TypedDict("dict1", _)))

  private lazy val typedNullGen = Gen.const(TypedNull)

  private lazy val unknownGen = Gen.const(Unknown)

  private lazy val allExceptUnionTypeGen: Gen[typing.TypingResult] =
    Gen.frequency(
      1 -> typedNullGen,
      1 -> unknownGen,
      // It is the most combinations
      50 -> singleTypingResultGen
    )

  private lazy val unionTypeGen = allExceptUnionTypeGen.flatMap(t1 => allExceptUnionTypeGen.map(t2 => Typed(t1, t2)))

  lazy val typingResultGen: Gen[TypingResult] =
    Gen.lzy {
      genFromListOfGens(
        optionalGen(features.unions, unionTypeGen) :::
          allExceptUnionTypeGen ::
          Nil
      )
    }

  private def toSingleTypingResult(gen: Gen[TypingResult]): Gen[SingleTypingResult] =
    gen.filter(_.isInstanceOf[SingleTypingResult]).map(_.asInstanceOf[SingleTypingResult])

  // Warning: it is not safe but it is easier to concatenate lists than NELs
  private def genFromListOfGens[T](list: List[Gen[T]]): Gen[T] = {
    Gen.choose(0, list.size - 1).flatMap(list(_))
  }

  private def optionalGen[T](shouldAdd: Boolean, gen: => Gen[T]): List[Gen[T]] = if (shouldAdd) List(gen) else Nil

  private def optionalGenWithFrequency[T](shouldAdd: Boolean, gen: => (Int, Gen[T])): List[(Int, Gen[T])] =
    if (shouldAdd) List(gen) else Nil

}

object TypingResultGen {

  def typingResultGen(features: EnabledTypedFeatures): Gen[TypingResult] = new TypingResultGen(features).typingResultGen

  class Animal

  class Pet extends Animal

  class Dog extends Pet

  class Cat extends Pet

  class Plant

  class Cactus extends Plant

}

// Rest of types are enabled by default
// TODO: add all combinations
case class EnabledTypedFeatures(
    unions: Boolean = true,
    taggedTypes: Boolean = true,
)

object EnabledTypedFeatures {
  val All: EnabledTypedFeatures = EnabledTypedFeatures()
}

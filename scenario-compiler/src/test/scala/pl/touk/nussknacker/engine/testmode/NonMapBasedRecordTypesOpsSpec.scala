package pl.touk.nussknacker.engine.testmode

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypedObjectWithValue, TypingResult}

import java.util.{List => JList, Map => JMap}
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

class NonMapBasedRecordTypesOpsSpec extends AnyFunSuiteLike with Matchers {

  import NonMapBasedRecordTypesOps._

  private val sampleFields = List("foo" -> Typed[String], "bar" -> Typed[Int])

  test("should translate nom-Map-based record to Map-based record") {
    nonMapBasedRecord(sampleFields).toMapBasedRecordTypes shouldBe Typed.record(sampleFields)
  }

  test("should translate nom-Map-based record to Map-based record nested inside record") {
    val nestedNonMapBasedRecord =
      Typed.record(Seq("nestedRecord" -> nonMapBasedRecord(sampleFields), "otherField" -> Typed[Int]))
    val expectedType = Typed.record(Seq("nestedRecord" -> Typed.record(sampleFields), "otherField" -> Typed[Int]))
    nestedNonMapBasedRecord.toMapBasedRecordTypes shouldBe expectedType
  }

  test("should translate nom-Map-based record to Map-based record nested inside non-Map-based record") {
    val nestedNonMapBasedRecord =
      nonMapBasedRecord(Seq("nestedRecord" -> nonMapBasedRecord(sampleFields), "otherField" -> Typed[Int]))
    val expectedType = Typed.record(Seq("nestedRecord" -> Typed.record(sampleFields), "otherField" -> Typed[Int]))
    nestedNonMapBasedRecord.toMapBasedRecordTypes shouldBe expectedType
  }

  test("should translate nom-Map-based record to Map-based record used inside generic type") {
    val nestedNonMapBasedRecord = Typed.genericTypeClass[JList[_]](List(nonMapBasedRecord(sampleFields)))
    val expectedType            = Typed.genericTypeClass[JList[_]](List(Typed.record(sampleFields)))
    nestedNonMapBasedRecord.toMapBasedRecordTypes shouldBe expectedType
  }

  test("should not accept translation of nom-Map-based record used inside generic type with value") {
    val nonMapBasedRecordUsedInGenericTypeWithValue = TypedObjectWithValue(
      Typed.genericTypeClass[JList[_]](List(nonMapBasedRecord(sampleFields))),
      List(SomeRecordClass("ala", 123)).asJava
    )
    an[Exception] shouldBe thrownBy {
      nonMapBasedRecordUsedInGenericTypeWithValue.toMapBasedRecordTypes
    }
  }

  test("should accept translation of type with value that doesn't use non-Map-based record") {
    val typeWithValue = TypedObjectWithValue(Typed.typedClass[String], "ala")
    typeWithValue.toMapBasedRecordTypes shouldBe typeWithValue
  }

  // This tests simulates translation of InputMeta which extends TypedObjectTypingResult - see notice there
  test("should not change implementing class when translating Map-based-record to Map-based-record") {
    new CustomRecordClass(sampleFields).toMapBasedRecordTypes shouldBe a[CustomRecordClass]
  }

  private def nonMapBasedRecord(fields: Seq[(String, TypingResult)]) =
    Typed.record(fields, Typed.typedClass[SomeRecordClass])

  case class SomeRecordClass(foo: String, bar: Int)

  class CustomRecordClass(fields: Seq[(String, TypingResult)])
      extends TypedObjectTypingResult(ListMap(fields: _*), Typed.typedClass[JMap[_, _]])

}

package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.aggregates.{FirstAggregator, LastAggregator, ListAggregator, MapAggregator, MaxAggregator, MinAggregator, SetAggregator, SumAggregator}

import java.lang.{Integer => JInt, Long => JLong}
import java.util.{List => JList, Map => JMap, Set => JSet}
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap

class AggregatesSpec extends FunSuite with TableDrivenPropertyChecks with Matchers {

  private val justAnyObject = new JustAnyClass
  private val aggregators = Table(
    ("aggregate", "input", "obj", "stored", "output"),
    (HyperLogLogPlusAggregator(), Typed[String], "", Typed[HyperLogLogPlusWrapper], Typed[Long]),
    (SumAggregator, Typed[Int], 1, Typed[JLong], Typed[JLong]),

    (MinAggregator, Typed[Int], 1, Typed[JInt], Typed[JInt]),
    (MaxAggregator, Typed[Int], 1, Typed[JInt], Typed[JInt]),
    (FirstAggregator, Typed[JustAnyClass], justAnyObject, Typed.fromDetailedType[Option[JustAnyClass]], Typed[JustAnyClass]),
    (LastAggregator, Typed[JustAnyClass], justAnyObject, Typed[JustAnyClass], Typed[JustAnyClass]),
    (SetAggregator, Typed[JustAnyClass], justAnyObject, Typed.fromDetailedType[Set[JustAnyClass]], Typed.fromDetailedType[JSet[JustAnyClass]]),
    (ListAggregator, Typed[JustAnyClass], justAnyObject, Typed.fromDetailedType[List[JustAnyClass]], Typed.fromDetailedType[JList[JustAnyClass]])
  )

  private def checkAggregator(aggregator: Aggregator, input: TypingResult, el: Any, stored: TypingResult, output: TypingResult): Unit = {
    aggregator.computeStoredType(input).getOrElse(fail("Failed to compute output type")) shouldBe stored
    aggregator.computeOutputType(input).getOrElse(fail("Failed to compute output type")) shouldBe output

    val computed = aggregator.addElement(el.asInstanceOf[aggregator.Element], aggregator.zero)
    //TODO: powinno byc ==?
    shouldBeInstanceOf(computed, stored)
    shouldBeInstanceOf(aggregator.result(computed), output)

    val zero = aggregator.zero
    shouldBeInstanceOf(aggregator.zero, stored)
    shouldBeInstanceOf(aggregator.result(zero), output)
  }

  private def shouldBeInstanceOf(obj: Any, typ: TypingResult): Unit = {
    val typeFromInstance = Typed.fromInstance(obj) match {
      //when e.g. e == null we want to compare as to Unknown
      case e if e == Typed.empty => Unknown
      //when e.g. e == empty list we want to compare as to Unknown
      case TypedClass(obj, param :: Nil) if param == Typed.empty => TypedClass(obj, List(Unknown))
      case a => a
    }
    val canBeSubclassCase = typeFromInstance.canBeSubclassOf(typ)
    val typedObjectCase = typ.isInstanceOf[TypedObjectTypingResult] && typeFromInstance.canBeSubclassOf(typ.asInstanceOf[TypedObjectTypingResult].objType)
    (canBeSubclassCase || typedObjectCase) shouldBe true
  }


  test("should compute output and stored type for simple aggregators") {
    forAll(aggregators)(checkAggregator)
  }

  test("should compute output and store type for map aggregate") {

    val namedAggregators = aggregators.indices.map(id => s"field$id").zip(aggregators).tail.toMap

    val mapAggregator = new MapAggregator(namedAggregators.mapValues(_._1.asInstanceOf[Aggregator]).asJava)
    val input = TypedObjectTypingResult(ListMap(namedAggregators.mapValues(_._2).toList: _*), objType = Typed.typedClass[JMap[_, _]])
    val el = namedAggregators.mapValues(_._3).asJava
    val stored = TypedObjectTypingResult(ListMap(namedAggregators.mapValues(_._4).toList: _*), objType = TypedClass(classOf[Map[_, _]], List(Typed[String], Unknown)))
    val output = TypedObjectTypingResult(ListMap(namedAggregators.mapValues(_._5).toList: _*), objType = TypedClass(classOf[JMap[_, _]], List(Typed[String], Unknown)))
    checkAggregator(mapAggregator, input, el, stored, output)
  }

  test("MapAggregator manages field add/removal") {

    val aggregator = new MapAggregator(Map[String, Aggregator]("field1" -> SumAggregator, "field2" -> MaxAggregator).asJava)

    val oldState = Map[String, AnyRef]("field1" -> (5: java.lang.Integer), "field0" -> "ddd")

    def resultFor(maps: Map[String, Any]*): AnyRef = aggregator.getResult(
      maps.map(_.mapValues(_.asInstanceOf[AnyRef]).asJava).foldRight(oldState)(aggregator.addElement)
    )

    resultFor() shouldBe Map("field1" -> 5, "field2" -> null).asJava

    resultFor(
      Map("field1" -> 4, "field2" -> 3)
    ) shouldBe Map("field1" -> 9L, "field2" -> 3).asJava

    resultFor(
      Map("field1" -> 3, "field2" -> 7, "field7" -> 5),
      Map("field1" -> 4, "field2" -> 3)
    ) shouldBe Map("field1" -> 12L, "field2" -> 7).asJava

  }

  class JustAnyClass


}

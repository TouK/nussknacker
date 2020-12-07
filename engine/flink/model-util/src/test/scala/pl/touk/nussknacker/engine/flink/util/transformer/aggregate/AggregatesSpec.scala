package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypedUnion, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.aggregates.{FirstAggregator, LastAggregator, ListAggregator, MapAggregator, MaxAggregator, MinAggregator, SetAggregator, SumAggregator}
import java.lang.{Integer => JInt, Long => JLong}
import java.util.{List => JList, Map => JMap, Set => JSet}

import scala.collection.JavaConverters._

class AggregatesSpec extends FunSuite with TableDrivenPropertyChecks with Matchers {
  
  class JustAnyClass

  private val justAnyObject = new JustAnyClass

  private val aggregators = Table(
    ("aggregate", "input", "obj", "stored", "output"),
    (HyperLogLogPlusAggregator(), Typed[String], "", Typed[CardinalityWrapper], Typed[Long]),
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


  test("should compute output and stored type for simple aggregators") {
    forAll(aggregators)(checkAggregator)
  }

  test("should compute output and store type for map aggregate") {

    val namedAggregators = aggregators.indices.map(id => s"field$id").zip(aggregators).tail.toMap

    val mapAggregator = new MapAggregator(namedAggregators.mapValues(_._1.asInstanceOf[Aggregator]).asJava)
    val input = TypedObjectTypingResult(namedAggregators.mapValues(_._2), objType = Typed.typedClass[JMap[_, _]])
    val el= namedAggregators.mapValues(_._3).asJava
    val stored = TypedObjectTypingResult(namedAggregators.mapValues(_._4), objType = Typed.typedClass[Map[_, _]])
    val output = TypedObjectTypingResult(namedAggregators.mapValues(_._5), objType = Typed.typedClass[JMap[_, _]])
    checkAggregator(mapAggregator, input, el, stored, output)
  }

  private def shouldBeInstanceOf(obj: Any, typ: TypingResult): Unit = {
    val typeFromInstance = Typed.fromInstance(obj) match {
      //when e.g. e == null we want to compare as to Unknown
      case e if e == Typed.empty => Unknown
      //when e.g. e == empty list we want to compare as to Unknown
      case TypedClass(obj, param::Nil) if param == Typed.empty => TypedClass(obj, List(Unknown))
      case a => a  
    }
    val canBeSubclassCase= typeFromInstance.canBeSubclassOf(typ)
    val typedObjectCase = typ.isInstanceOf[TypedObjectTypingResult] && typeFromInstance.canBeSubclassOf(typ.asInstanceOf[TypedObjectTypingResult].objType)
    (canBeSubclassCase || typedObjectCase) shouldBe true
  }


}

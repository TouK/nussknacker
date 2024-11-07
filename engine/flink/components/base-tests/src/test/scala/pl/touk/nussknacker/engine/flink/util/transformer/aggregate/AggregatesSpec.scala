package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.aggregates._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.lang.{Integer => JInt, Long => JLong}
import java.math.BigInteger
import java.util.{List => JList, Map => JMap, Set => JSet}
import scala.jdk.CollectionConverters._

class AggregatesSpec extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

  private val justAnyObject = new JustAnyClass

  private val aggregators = Table(
    ("aggregate", "input", "obj", "stored", "output"),
    (HyperLogLogPlusAggregator(), Typed[String], "", Typed[HyperLogLogPlusWrapper], Typed[Long]),
    (SumAggregator, Typed[Int], 1, Typed[JLong], Typed[JLong]),
    (MinAggregator, Typed[Int], 1, Typed[JInt], Typed[JInt]),
    (MaxAggregator, Typed[Int], 1, Typed[JInt], Typed[JInt]),
    (
      FirstAggregator,
      Typed[JustAnyClass],
      justAnyObject,
      Typed.fromDetailedType[Option[JustAnyClass]],
      Typed[JustAnyClass]
    ),
    (
      LastAggregator,
      Typed[JustAnyClass],
      justAnyObject,
      Typed.fromDetailedType[Option[JustAnyClass]],
      Typed[JustAnyClass]
    ),
    (
      SetAggregator,
      Typed[JustAnyClass],
      justAnyObject,
      Typed.fromDetailedType[Set[JustAnyClass]],
      Typed.fromDetailedType[JSet[JustAnyClass]]
    ),
    (
      ListAggregator,
      Typed[JustAnyClass],
      justAnyObject,
      Typed.fromDetailedType[List[JustAnyClass]],
      Typed.fromDetailedType[JList[JustAnyClass]]
    )
  )

  private def checkAggregator(
      aggregator: Aggregator,
      input: TypingResult,
      el: Any,
      stored: TypingResult,
      output: TypingResult
  ): Unit = {
    aggregator.computeStoredType(input).getOrElse(fail("Failed to compute output type")) shouldBe stored
    aggregator.computeOutputType(input).getOrElse(fail("Failed to compute output type")) shouldBe output

    val computed = aggregator.addElement(el.asInstanceOf[aggregator.Element], aggregator.zero)
    // TODO: powinno byc ==?
    shouldBeInstanceOf(computed, stored)
    shouldBeInstanceOf(aggregator.result(computed), output)

    val zero = aggregator.zero
    shouldBeInstanceOf(aggregator.zero, stored)
    shouldBeInstanceOf(aggregator.result(zero), output)
  }

  private def shouldBeInstanceOf(obj: Any, typ: TypingResult): Unit = {
    val typeFromInstance  = Typed.fromInstance(obj)
    val canBeSubclassCase = typeFromInstance.canBeImplicitlyConvertedTo(typ)
    val typedObjectCase = typ.isInstanceOf[TypedObjectTypingResult] && typeFromInstance.canBeImplicitlyConvertedTo(
      typ.asInstanceOf[TypedObjectTypingResult].runtimeObjType
    )
    (canBeSubclassCase || typedObjectCase) shouldBe true
  }

  test("should calculate correct results for first aggregator") {
    val agg = FirstAggregator
    agg.result(
      agg.addElement(8.asInstanceOf[agg.Element], agg.addElement(5.asInstanceOf[agg.Element], agg.zero))
    ) shouldEqual 5
  }

  test("should calculate correct results for countWhen aggregator") {
    val agg = CountWhenAggregator
    agg.result(
      agg.addElement(
        false.asInstanceOf[agg.Element],
        agg.addElement(true.asInstanceOf[agg.Element], agg.addElement(true.asInstanceOf[agg.Element], agg.zero))
      )
    ) shouldEqual 2
  }

  test("should calculate correct results for average aggregator") {
    val agg = AverageAggregator
    agg.result(
      agg.addElement(8.asInstanceOf[agg.Element], agg.addElement(7.asInstanceOf[agg.Element], agg.zero))
    ) shouldEqual 7.5
  }

  test("should calculate correct results for average aggregator on BigInt") {
    val agg = AverageAggregator
    agg.result(
      agg.addElement(
        new BigInteger("8").asInstanceOf[agg.Element],
        agg.addElement(new BigInteger("7").asInstanceOf[agg.Element], agg.zero)
      )
    ) shouldEqual new java.math.BigDecimal("7.5")
  }

  test("should calculate correct results for average aggregator on float") {
    val agg = AverageAggregator
    agg.result(
      agg.addElement(
        8.0f.asInstanceOf[agg.Element],
        agg.addElement(7.0f.asInstanceOf[agg.Element], agg.zero)
      )
    ) shouldEqual 7.5
  }

  test("should calculate correct results for average aggregator on BigDecimal") {
    val agg = AverageAggregator
    agg.result(
      agg.addElement(
        new java.math.BigDecimal("8").asInstanceOf[agg.Element],
        agg.addElement(new java.math.BigDecimal("7").asInstanceOf[agg.Element], agg.zero)
      )
    ) shouldEqual new java.math.BigDecimal("7.5")
  }

  test("AverageAggregator should calculate correct results for empty aggregation set") {
    val agg = AverageAggregator
    val result = agg.result(
      agg.zero
    )

    // null is returned because method alignToExpectedType did not run
    require(result == null)
  }

  test("should calculate correct results for last aggregator") {
    val agg = LastAggregator
    agg.result(
      agg.addElement(8.asInstanceOf[agg.Element], agg.addElement(5.asInstanceOf[agg.Element], agg.zero))
    ) shouldEqual 8
  }

  test("should compute output and stored type for simple aggregators") {
    forAll(aggregators)(checkAggregator)
  }

  test("should compute output and store type for map aggregate") {

    val namedAggregators = aggregators.indices.map(id => s"field$id").zip(aggregators).tail.toMap

    val mapAggregator =
      new MapAggregator(namedAggregators.mapValuesNow(_._1).asJava.asInstanceOf[JMap[String, Aggregator]])
    val input = Typed.record(namedAggregators.mapValuesNow(_._2), objType = Typed.typedClass[JMap[_, _]])
    val el    = namedAggregators.mapValuesNow(_._3).asJava
    val stored = Typed.record(
      namedAggregators.mapValuesNow(_._4),
      objType = Typed.genericTypeClass(classOf[Map[_, _]], List(Typed[String], Unknown))
    )
    val output = Typed.record(
      namedAggregators.mapValuesNow(_._5),
      objType = Typed.genericTypeClass(classOf[JMap[_, _]], List(Typed[String], Unknown))
    )
    checkAggregator(mapAggregator, input, el, stored, output)
  }

  test("should compute output and store type for option aggregate") {
    val aggregator = ListAggregator

    val optionAggregator     = new OptionAggregator(aggregator)
    val input                = Typed.fromDetailedType[Option[Double]]
    val elem: Option[Double] = Some(1.0)
    val stored               = Typed.fromDetailedType[Option[List[Double]]]
    val output               = Typed.fromDetailedType[java.util.List[Double]]

    checkAggregator(optionAggregator, input, elem, stored, output)
  }

  test("MapAggregator manages field add/removal") {

    val aggregator =
      new MapAggregator(Map[String, Aggregator]("field1" -> SumAggregator, "field2" -> MaxAggregator).asJava)

    val oldState = Map[String, AnyRef]("field1" -> (5: java.lang.Integer), "field0" -> "ddd")

    def resultFor(maps: Map[String, Any]*): AnyRef = aggregator.getResult(
      maps.map(_.mapValuesNow(_.asInstanceOf[AnyRef]).asJava).foldRight(oldState)(aggregator.addElement)
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

  test("Neutral elements for accumulator should be detected for sum") {
    val aggregator = SumAggregator
    val oldState   = 123

    aggregator.isNeutralForAccumulator(0, oldState) shouldBe true
    aggregator.isNeutralForAccumulator(1, oldState) shouldBe false
  }

  test("Neutral elements for accumulator should be detected for set") {
    val aggregator = SetAggregator
    val oldState   = Set[AnyRef]("123")

    aggregator.isNeutralForAccumulator("123", oldState) shouldBe true
    aggregator.isNeutralForAccumulator("aaa", oldState) shouldBe false
  }

  test("Neutral elements for accumulator should be detected for max") {
    val aggregator = MaxAggregator
    val oldState   = 123

    aggregator.isNeutralForAccumulator(0, oldState) shouldBe true
    aggregator.isNeutralForAccumulator(123, oldState) shouldBe true
    aggregator.isNeutralForAccumulator(234, oldState) shouldBe false
  }

  test("Neutral elements for accumulator should be detected for min") {
    val aggregator = MinAggregator
    val oldState   = 123

    aggregator.isNeutralForAccumulator(0, oldState) shouldBe false
    aggregator.isNeutralForAccumulator(123, oldState) shouldBe true
    aggregator.isNeutralForAccumulator(234, oldState) shouldBe true
  }

  test("Neutral elements for accumulator should be detected for first") {
    val aggregator = FirstAggregator

    aggregator.isNeutralForAccumulator("aaa", aggregator.zero) shouldBe false
    aggregator.isNeutralForAccumulator("bbb", Some("aaa")) shouldBe true
  }

  test("Neutral elements for accumulator should be detected for last") {
    val aggregator = LastAggregator

    aggregator.isNeutralForAccumulator("aaa", aggregator.zero) shouldBe false
    aggregator.isNeutralForAccumulator("bbb", Some("aaa")) shouldBe false
  }

  test("Neutral elements for accumulator should be detected for map") {
    val aggregator =
      new MapAggregator(Map[String, Aggregator]("sumField" -> SumAggregator, "maxField" -> MaxAggregator).asJava)

    val oldState = Map[String, AnyRef]("sumField" -> (5: java.lang.Integer), "maxField" -> (123: java.lang.Integer))

    aggregator.isNeutralForAccumulator(
      Map[String, AnyRef]("sumField" -> (0: java.lang.Integer), "maxField" -> (123: java.lang.Integer)).asJava,
      oldState
    ) shouldBe true
    aggregator.isNeutralForAccumulator(
      Map[String, AnyRef]("sumField" -> (1: java.lang.Integer), "maxField" -> (123: java.lang.Integer)).asJava,
      oldState
    ) shouldBe false
    aggregator.isNeutralForAccumulator(
      Map[String, AnyRef]("sumField" -> (0: java.lang.Integer), "maxField" -> (124: java.lang.Integer)).asJava,
      oldState
    ) shouldBe false
  }

  test("Neutral elements for accumulator should be detected for option") {
    val aggregatorInner = SumAggregator

    val aggregator = new OptionAggregator(aggregatorInner)

    val oldState = Some(45)

    aggregator.isNeutralForAccumulator(
      Some(8).asInstanceOf[aggregator.Element],
      oldState.asInstanceOf[aggregator.Aggregate]
    ) shouldBe false
    aggregator.isNeutralForAccumulator(
      None.asInstanceOf[aggregator.Element],
      oldState.asInstanceOf[aggregator.Aggregate]
    ) shouldBe true
    aggregator.isNeutralForAccumulator(
      Some(0).asInstanceOf[aggregator.Element],
      oldState.asInstanceOf[aggregator.Aggregate]
    ) shouldBe true
  }

  test("Zeros should be neutral for simple aggregators") {
    forAll(aggregators)({ (aggregator, _, el, _, _) =>
      {
        val elem           = el.asInstanceOf[aggregator.Element]
        val elemAggregator = aggregator.addElement(elem, aggregator.zero)

        // There is no generic way of checking if val.add(0) == val
        // or 0.add(val) == 0.

        aggregator.mergeAggregates(elemAggregator, aggregator.zero) shouldBe elemAggregator
        aggregator.mergeAggregates(aggregator.zero, elemAggregator) shouldBe elemAggregator
      }
    })
  }

  test("Zeros should be neutral for map aggregator") {
    val aggregator =
      new MapAggregator(Map[String, Aggregator]("sumField" -> SumAggregator, "maxField" -> MaxAggregator).asJava)

    val state = Map[String, AnyRef]("sumField" -> (5: java.lang.Integer), "maxField" -> (123: java.lang.Integer))

    aggregator.mergeAggregates(aggregator.zero, state) shouldBe state
    aggregator.mergeAggregates(state, aggregator.zero) shouldBe state
  }

  test("Zeros should be neutral for option aggregator") {
    val aggregatorInner = SumAggregator

    val aggregator = new OptionAggregator(aggregatorInner)
    val state      = Some(65).asInstanceOf[aggregator.Aggregate]

    aggregator.mergeAggregates(aggregator.zero, state) shouldBe state
    aggregator.mergeAggregates(state, aggregator.zero) shouldBe state

    val leftElem       = Some(15).asInstanceOf[aggregator.Element]
    val leftElemState  = aggregator.addElement(leftElem, aggregator.zero)
    val rightElem      = Some(11).asInstanceOf[aggregator.Element]
    val rightElemState = aggregator.addElement(rightElem, aggregator.zero)
    val combinedState  = aggregator.addElement(rightElem, leftElemState)

    aggregator.mergeAggregates(leftElemState, rightElemState) shouldBe combinedState
    aggregator.mergeAggregates(rightElemState, leftElemState) shouldBe combinedState
  }

  class JustAnyClass
}

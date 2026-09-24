package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.api.typeinformation.{NullableTypeInfo, TypeInformationDetection}
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.aggregates._

import scala.jdk.CollectionConverters._

class AggregatorStateSerializationSpec
    extends AnyFunSuite
    with Matchers
    with TableDrivenPropertyChecks
    with FlinkTypeInformationSerializationMixin {

  private val mapAggregator =
    new MapAggregator(Map[String, Aggregator]("sum" -> SumAggregator, "list" -> ListAggregator).asJava)

  private val mapInput = Typed.record(Map("sum" -> Typed[Int], "list" -> Typed[String]))

  private val optionInput = Typed.genericTypeClass(classOf[Option[_]], List(Typed[String]))

  private val numbersWithNull = List[AnyRef](1: java.lang.Integer, null, 2: java.lang.Integer)

  private val stringsWithNull = List[AnyRef]("a", null, "b")

  private val cases = Table(
    ("name", "aggregator", "input", "elements"),
    ("sum", SumAggregator, Typed[Int], numbersWithNull),
    ("min", MinAggregator, Typed[Int], numbersWithNull),
    ("max", MaxAggregator, Typed[Int], numbersWithNull),
    ("countWhen", CountWhenAggregator, Typed[Boolean], List[AnyRef](java.lang.Boolean.TRUE, null)),
    ("average", AverageAggregator, Typed[Int], numbersWithNull),
    ("stddevPop", PopulationStandardDeviationAggregator, Typed[Int], numbersWithNull),
    ("varSamp", SampleVarianceAggregator, Typed[Int], numbersWithNull),
    ("median", MedianAggregator, Typed[Int], numbersWithNull),
    ("list", ListAggregator, Typed[String], stringsWithNull),
    ("set", SetAggregator, Typed[String], stringsWithNull),
    ("first with a null element", FirstAggregator, Typed[String], List[AnyRef](null, "a")),
    ("last with a null element", LastAggregator, Typed[String], List[AnyRef]("a", null)),
    ("approxCardinality", HyperLogLogPlusAggregator(), Typed[String], stringsWithNull),
    (
      "map",
      mapAggregator,
      mapInput,
      List[AnyRef](Map[String, AnyRef]("sum" -> null, "list" -> "a").asJava)
    ),
    ("option", new OptionAggregator(ListAggregator), optionInput, List[AnyRef](Some("a"), None, Some(null)))
  )

  // Kryo is allowed on purpose: `TypingResultAwareTypeInformationDetection` has no case for a scala `List`, `Set`,
  // `Option` or a `java.util.ArrayList`, so the stored type of median, list, set, first, last, map and option resolves
  // to a generic type info. Giving those a dedicated one is a separate change with its own state migration.
  test("an aggregate that saw a null element is serialized") {
    forAll(cases) { (_: String, aggregator: Aggregator, input: TypingResult, elements: List[AnyRef]) =>
      val accumulator = elements.foldLeft[AnyRef](aggregator.zero) { (acc, element) =>
        aggregator.add(element, acc)
      }
      val typeInfo = TypeInformationDetection.instance.forType[AnyRef](aggregator.computeStoredTypeUnsafe(input))

      serializeRoundTrip(accumulator, typeInfo, executionConfigWithKryo)()
    }
  }

  test("an accumulator that is still null is serialized through the AggregatingState type information") {
    val aggregators = Table("aggregator", SumAggregator, MinAggregator, MaxAggregator)

    forAll(aggregators) { aggregator: Aggregator =>
      aggregator.zero shouldBe null

      val storedTypeInfo = TypeInformationDetection.instance
        .forType[AnyRef](aggregator.computeStoredTypeUnsafe(Typed[Int]))

      serializeRoundTrip[AnyRef](aggregator.zero, new NullableTypeInfo(storedTypeInfo), executionConfigWithKryo)()
    }
  }

  test("list aggregate keeps a null element") {
    val accumulator = List[AnyRef]("a", null, "b").foldLeft(ListAggregator.zero) { (acc, element) =>
      ListAggregator.addElement(element, acc)
    }
    val typeInfo = TypeInformationDetection.instance
      .forType[AnyRef](ListAggregator.computeStoredTypeUnsafe(Typed[String]))

    getSerializeRoundTrip[AnyRef](accumulator, typeInfo, executionConfigWithKryo) shouldBe List("b", null, "a")
  }

}

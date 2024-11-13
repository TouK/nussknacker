package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import cats.instances.list._
import org.apache.flink.api.common.typeinfo.TypeInfo
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy.ForLargeFloatingNumbersOperation
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.typed.{NumberTypeUtils, typing}
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.CaseClassTypeInfoFactory
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.MathUtils
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._

import java.util
import scala.jdk.CollectionConverters._

/*
  This class lists some simple aggregates
 */
object aggregates {

  object SumAggregator extends ReducingAggregator with MathAggregator {

    override type Element = Number

    override def zero: Number = null

    override def isNeutralForAccumulator(element: Element, currentAggregate: Aggregate): Boolean =
      element.doubleValue() == 0.0

    override def addElement(n1: Number, n2: Number): Number = MathUtils.largeSum(n1, n2)

    override protected val promotionStrategy: NumberTypesPromotionStrategy =
      NumberTypesPromotionStrategy.ForLargeNumbersOperation

    override def alignToExpectedType(value: AnyRef, outputType: TypingResult): AnyRef = {
      if (value == null) {
        NumberTypeUtils.zeroForType(outputType)
      } else {
        value
      }
    }

  }

  object MaxAggregator extends ReducingAggregator with MathAggregator {

    override type Element = Number

    override def zero: Number = null

    override def addElement(n1: Number, n2: Number): Number = MathUtils.max(n1, n2)

    override protected val promotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ForMinMax

  }

  object MinAggregator extends ReducingAggregator with MathAggregator {

    override type Element = Number

    override def zero: Number = null

    override def addElement(n1: Number, n2: Number): Number = MathUtils.min(n1, n2)

    override protected val promotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ForMinMax

  }

  object ListAggregator extends Aggregator {

    override type Aggregate = List[AnyRef]

    override type Element = AnyRef

    override def zero: Aggregate = List.empty

    override def isNeutralForAccumulator(element: ListAggregator.Element, currentAggregate: List[AnyRef]): Boolean =
      false

    // append instead of prepend (assess performance considerations...)
    override def addElement(el: Element, agg: Aggregate): Aggregate = el :: agg

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1 ++ agg2

    override def result(finalAggregate: Aggregate): AnyRef = new java.util.ArrayList[Any](finalAggregate.asJava)

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult] = Valid(
      Typed.genericTypeClass[java.util.List[_]](List(input))
    )

    override def computeStoredType(input: TypingResult): Validated[String, TypingResult] = Valid(
      Typed.genericTypeClass[List[_]](List(input))
    )

  }

  object SetAggregator extends Aggregator {

    override type Aggregate = Set[AnyRef]

    override type Element = AnyRef

    override def zero: Aggregate = Set.empty

    override def isNeutralForAccumulator(element: SetAggregator.Element, currentAggregate: Set[AnyRef]): Boolean =
      currentAggregate.contains(element)

    override def addElement(el: Element, agg: Aggregate): Aggregate = agg + el

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1 ++ agg2

    override def result(finalAggregate: Aggregate): AnyRef = new java.util.HashSet(finalAggregate.asJava)

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult] = Valid(
      Typed.genericTypeClass[java.util.Set[_]](List(input))
    )

    override def computeStoredType(input: TypingResult): Validated[String, TypingResult] = Valid(
      Typed.genericTypeClass[Set[_]](List(input))
    )

  }

  object FirstAggregator extends Aggregator {
    override type Aggregate = Option[AnyRef]

    override type Element = AnyRef

    override def zero: Aggregate = None

    override def isNeutralForAccumulator(element: FirstAggregator.Element, currentAggregate: Option[AnyRef]): Boolean =
      currentAggregate.isDefined

    override def addElement(el: Element, agg: Aggregate): Aggregate = agg.orElse(Some(el))

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1.orElse(agg2)

    override def result(finalAggregate: Aggregate): AnyRef = finalAggregate.orNull

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult] = Valid(input)

    override def computeStoredType(input: TypingResult): Validated[String, TypingResult] = Valid(
      Typed.genericTypeClass(classOf[Option[_]], List(input))
    )

  }

  object LastAggregator extends Aggregator {
    override type Aggregate = Option[AnyRef]

    override type Element = AnyRef

    override def zero: Aggregate = None

    override def isNeutralForAccumulator(
        element: LastAggregator.Element,
        currentAggregate: LastAggregator.Aggregate
    ): Boolean = false

    override def addElement(el: Element, agg: Aggregate): Aggregate = Some(el)

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg2.orElse(agg1)

    override def result(finalAggregate: Aggregate): AnyRef = finalAggregate.orNull

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult] = Valid(input)

    override def computeStoredType(input: TypingResult): Validated[String, TypingResult] = Valid(
      Typed.genericTypeClass(classOf[Option[_]], List(input))
    )

  }

  object CountWhenAggregator extends Aggregator {

    override type Element = java.lang.Boolean

    override type Aggregate = java.lang.Long

    override def zero: java.lang.Long = 0L

    override def addElement(element: java.lang.Boolean, aggregate: Aggregate): Aggregate =
      if (element) aggregate + 1 else aggregate

    override def mergeAggregates(aggregate1: Aggregate, aggregate2: Aggregate): Aggregate = aggregate1 + aggregate2

    override def result(finalAggregate: Aggregate): AnyRef = finalAggregate

    override def computeOutputType(input: typing.TypingResult): Validated[String, typing.TypingResult] = {
      if (input.canBeSubclassOf(Typed[Boolean])) {
        Valid(Typed[Long])
      } else {
        Invalid(s"Invalid aggregate type: ${input.display}, should be: ${Typed[Boolean].display}")
      }
    }

    override def computeStoredType(input: typing.TypingResult): Validated[String, typing.TypingResult] =
      computeOutputType(input)
  }

  object AverageAggregator extends Aggregator {

    override type Element = java.lang.Number

    override type Aggregate = AverageAggregatorState

    override def zero: AverageAggregatorState = AverageAggregatorState(null, 0)

    override def addElement(element: java.lang.Number, aggregate: Aggregate): Aggregate =
      AverageAggregatorState(MathUtils.largeFloatingSum(element, aggregate.sum), aggregate.count + 1)

    override def mergeAggregates(aggregate1: Aggregate, aggregate2: Aggregate): Aggregate =
      AverageAggregatorState(
        MathUtils.largeFloatingSum(aggregate1.sum, aggregate2.sum),
        aggregate1.count + aggregate2.count
      )

    override def result(finalAggregate: Aggregate): AnyRef = {
      val count = finalAggregate.count
      finalAggregate.sum match {
        case null =>
          // will be replaced to Double.Nan in alignToExpectedType iff return type is known to be Double
          null
        case sum: java.lang.Double     => (sum / count).asInstanceOf[AnyRef]
        case sum: java.math.BigDecimal => (BigDecimal(sum) / BigDecimal(count)).bigDecimal
      }
    }

    override def alignToExpectedType(value: AnyRef, outputType: TypingResult): AnyRef = {
      if (value == null && outputType == Typed(classOf[Double])) {
        Double.NaN.asInstanceOf[AnyRef]
      } else {
        value
      }
    }

    override def computeOutputType(input: typing.TypingResult): Validated[String, typing.TypingResult] = {

      if (!input.canBeSubclassOf(Typed[Number])) {
        Invalid(s"Invalid aggregate type: ${input.display}, should be: ${Typed[Number].display}")
      } else {
        Valid(ForLargeFloatingNumbersOperation.promoteSingle(input))
      }
    }

    override def computeStoredType(input: typing.TypingResult): Validated[String, typing.TypingResult] =
      Valid(Typed[AverageAggregatorState])

    @TypeInfo(classOf[AverageAggregatorState.TypeInfoFactory])
    // it would be natural to have one field sum: Number instead of nullable sumDouble and sumBigDecimal,
    // it is done this way to have types serialized properly
    case class AverageAggregatorState(
        sumDouble: java.lang.Double,
        sumBigDecimal: java.math.BigDecimal,
        count: java.lang.Long
    ) {
      def sum: Number = Option(sumDouble).getOrElse(sumBigDecimal)
    }

    object AverageAggregatorState {
      class TypeInfoFactory extends CaseClassTypeInfoFactory[AverageAggregatorState]

      def apply(sum: Number, count: java.lang.Long): AverageAggregatorState = {
        sum match {
          case null                                => AverageAggregatorState(null, null, count)
          case sumDouble: java.lang.Double         => AverageAggregatorState(sumDouble, null, count)
          case sumBigDecimal: java.math.BigDecimal => AverageAggregatorState(null, sumBigDecimal, count)
        }
      }

    }

  }

  /*
    This is more complex aggregator, as it is composed from smaller ones.
    The idea is that we define aggregator:
    #AGG.map({sumField: #AGG.sum, setField: #AGG.set})
    then aggregateBy:
    {sumField: #input.field1, setField: #input.field2}
    and in result we get
    {sumField: 11, setField: ['a', 'b']}
    - typed map with aggregations
    See TransformersTest for usage sample
    TODO: handling nulls more gracefully
   */
  class MapAggregator(fields: java.util.Map[String, Aggregator]) extends Aggregator {

    private val scalaFields = fields.asScala.toMap

    override type Element = java.util.Map[String, AnyRef]

    override type Aggregate = Map[String, AnyRef]

    override val zero: Aggregate = scalaFields.mapValuesNow(_.zero)

    override def isNeutralForAccumulator(el: util.Map[String, AnyRef], agg: Map[String, AnyRef]): Boolean =
      scalaFields.forall { case (field, aggregator) =>
        aggregator.isNeutralForAccumulator(
          el.get(field).asInstanceOf[aggregator.Element],
          agg.getOrElse(field, aggregator.zero).asInstanceOf[aggregator.Aggregate]
        )
      }

    override def addElement(el: Element, agg: Aggregate): Aggregate = scalaFields.map { case (field, aggregator) =>
      field -> aggregator.add(el.get(field), agg.getOrElse(field, aggregator.zero))
    }

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = scalaFields.map {
      case (field, aggregator) =>
        field -> aggregator.merge(agg1.getOrElse(field, aggregator.zero), agg2.getOrElse(field, aggregator.zero))
    }

    override def result(finalAggregate: Aggregate): AnyRef = scalaFields.map { case (field, aggregator) =>
      field -> aggregator.getResult(finalAggregate.getOrElse(field, aggregator.zero))
    }.asJava

    override def alignToExpectedType(value: AnyRef, outputType: TypingResult): AnyRef = {
      outputType match {
        case typedObj: TypedObjectTypingResult =>
          // here we assume the fields in value are equal to Aggregator fields
          value
            .asInstanceOf[java.util.Map[String, AnyRef]]
            .asScala
            .map { case (field, value) =>
              field -> scalaFields(field).alignToExpectedType(value, typedObj.fields(field))
            }
            .asJava
        case _ => value
      }
    }

    override def computeOutputType(input: TypingResult): Validated[String, TypedObjectTypingResult] =
      computeTypeByFields(
        input,
        Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Unknown)),
        _.computeOutputType(_)
      )

    override def computeStoredType(input: TypingResult): Validated[String, TypingResult] = computeTypeByFields(
      input,
      Typed.genericTypeClass(classOf[Map[_, _]], List(Typed[String], Unknown)),
      _.computeStoredType(_)
    )

    private def computeTypeByFields(
        input: TypingResult,
        objType: TypedClass,
        computeField: (Aggregator, TypingResult) => Validated[String, TypingResult]
    ): Validated[String, TypedObjectTypingResult] = {
      input match {
        case TypedObjectTypingResult(inputFields, klass, _)
            if inputFields.keySet == scalaFields.keySet && klass.canBeSubclassOf(Typed[java.util.Map[String, _]]) =>
          val validationRes = scalaFields
            .map { case (key, aggregator) =>
              computeField(aggregator, inputFields(key))
                .map(key -> _)
                .leftMap(m => NonEmptyList.of(s"$key - $m"))
            }
            .toList
            .sequence
            .leftMap(list => s"Invalid fields: ${list.toList.mkString(", ")}")
          validationRes.map(fields => Typed.record(fields, objType = objType))
        case TypedObjectTypingResult(inputFields, _, _) =>
          Invalid(
            s"Fields do not match, aggregateBy: ${inputFields.keys.mkString(", ")}, aggregator: ${scalaFields.keys.mkString(", ")}"
          )
        case _ =>
          Invalid("aggregateBy should be declared as fixed map")
      }
    }

  }

  /*
    Aggregator that wraps around another aggregator. Adds values to inner
    aggregator when it receives Some(val) and does nothing when it receives
    none.
   */
  class OptionAggregator(val agg: Aggregator) extends Aggregator {
    override type Aggregate = Option[agg.Aggregate]
    override type Element   = Option[agg.Element]

    override def zero: Aggregate = None

    override def isNeutralForAccumulator(element: Element, aggregate: Aggregate): Boolean =
      element.forall(agg.isNeutralForAccumulator(_, aggregate.getOrElse(agg.zero)))

    override def addElement(element: Element, aggregate: Aggregate): Aggregate =
      element.map(agg.addElement(_, aggregate.getOrElse(agg.zero))).orElse(aggregate)

    override def mergeAggregates(aggregate1: Aggregate, aggregate2: Aggregate): Aggregate =
      (aggregate1, aggregate2) match {
        case (Some(a1), Some(a2)) => Some(agg.mergeAggregates(a1, a2))
        case (a1 @ Some(_), None) => a1
        case (None, a2 @ Some(_)) => a2
        case (None, None)         => None
      }

    override def result(finalAggregate: Aggregate): AnyRef =
      agg.result(finalAggregate.getOrElse(agg.zero))

    override def computeOutputType(input: typing.TypingResult): Validated[String, typing.TypingResult] = input match {
      case TypedClass(input_type, input :: Nil, _) if input_type == classOf[Option[_]] =>
        agg.computeOutputType(input)
      case TypedClass(input_type, _, _) =>
        Invalid(s"input type must be Option")
      case _ =>
        Invalid(s"input has invalid type")
    }

    override def computeStoredType(input: typing.TypingResult): Validated[String, typing.TypingResult] = input match {
      case TypedClass(input_type, input :: Nil, _) if input_type == classOf[Option[_]] =>
        agg.computeStoredType(input).map(t => Typed.genericTypeClass[Option[_]](List(t)))
      case TypedClass(input_type, _, _) =>
        Invalid(s"input type must be Option")
      case _ =>
        Invalid(s"input has invalid type")
    }

  }

  abstract class ReducingAggregator extends Aggregator {

    override type Aggregate = Element

    override def mergeAggregates(aggregate: Aggregate, aggregate2: Aggregate): Element =
      addElement(aggregate, aggregate2)

    override def result(finalAggregate: Aggregate): Aggregate = finalAggregate

  }

  trait MathAggregator { self: ReducingAggregator =>

    override def computeOutputType(input: typing.TypingResult): Validated[String, typing.TypingResult] = {
      if (input.canBeSubclassOf(Typed[Number])) {
        // In some cases type can be promoted to other class e.g. Byte is promoted to Int for sum
        Valid(promotionStrategy.promoteSingle(input))
      } else {
        Invalid(s"Invalid aggregate type: ${input.display}, should be: ${Typed[Number].display}")
      }
    }

    override def computeStoredType(input: TypingResult): Validated[String, TypingResult] = computeOutputType(input)

    protected def promotionStrategy: NumberTypesPromotionStrategy

    protected def promotedType(typ: TypingResult): TypingResult = promotionStrategy.promoteSingle(typ)
  }

}

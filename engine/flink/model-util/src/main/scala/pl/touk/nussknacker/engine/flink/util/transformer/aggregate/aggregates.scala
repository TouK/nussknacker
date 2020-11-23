package pl.touk.nussknacker.engine.flink.util.transformer.aggregate
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{NumberTypeUtils, typing}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.MathUtils
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax

import scala.collection.JavaConverters._

/*
  This class lists some simple aggregates
 */
object aggregates {

  private val syntax = ValidatedSyntax[String]
  import syntax._

  object SumAggregator extends ReducingAggregator with MathAggregator {

    override type Element = Number

    override def zero: Number = null

    override def isNeutralForAccumulator(element: Number): Boolean =
      element.doubleValue() == 0.0

    override def addElement(n1: Number, n2: Number): Number = MathUtils.largeSum(n1, n2)

    override protected val promotionStrategy: NumberTypesPromotionStrategy = NumberTypesPromotionStrategy.ForLargeNumbersOperation

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

    override def zero: Aggregate = List()

    override def addElement(el: Element, agg: Aggregate): Aggregate = el::agg

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1 ++ agg2

    override def result(finalAggregate: Aggregate): AnyRef = new java.util.ArrayList[Any](finalAggregate.asJava)

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(Typed.genericTypeClass[java.util.List[_]](List(input)))

  }

  object SetAggregator extends Aggregator {

    override type Aggregate = Set[AnyRef]

    override type Element = AnyRef

    override def zero: Aggregate = Set()

    override def addElement(el: Element, agg: Aggregate): Aggregate = agg + el

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1 ++ agg2

    override def result(finalAggregate: Aggregate): AnyRef = new java.util.HashSet(finalAggregate.asJava)

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(Typed.genericTypeClass[java.util.Set[_]](List(input)))

  }

  object FirstAggregator extends Aggregator {

    private object Marker extends Serializable

    override type Aggregate = AnyRef

    override type Element = AnyRef

    override def zero: Aggregate = Marker

    override def addElement(el: Element, agg: Aggregate): Aggregate = if (agg == zero) el else agg

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1

    override def result(finalAggregate: Aggregate): AnyRef = if (finalAggregate == zero) null else finalAggregate

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(input)

  }

  object LastAggregator extends Aggregator {

    override type Aggregate = AnyRef

    override type Element = AnyRef

    override def zero: Aggregate = null

    override def addElement(el: Element, agg: Aggregate): Aggregate = el

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg2

    override def result(finalAggregate: Aggregate): AnyRef = finalAggregate

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(input)

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

    override def addElement(el: Element, agg: Aggregate): Aggregate = agg.map {
      case (field, value) => field -> scalaFields(field).add(el.get(field), value)
    }

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1.map {
      case (field, value) => field -> scalaFields(field).merge(value, agg2(field))
    }

    override def result(finalAggregate: Aggregate): AnyRef = finalAggregate.map {
      case (field, value) => field -> scalaFields(field).getResult(value)
    }.asJava


    override def alignToExpectedType(value: AnyRef, outputType: TypingResult): AnyRef = {
      outputType match {
        case typedObj: TypedObjectTypingResult =>
          value.asInstanceOf[java.util.Map[String, AnyRef]].asScala.map {
            case (field, value) =>
              field -> scalaFields(field).alignToExpectedType(value, typedObj.fields(field))
          }.asJava
        case _ => value
      }
    }

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult] = {
      input match {
        case TypedObjectTypingResult (inputFields, klass, _) if inputFields.keySet == scalaFields.keySet && klass.canBeSubclassOf(Typed[java.util.Map[String, _]])=>
          val validationRes = scalaFields.map { case (key, aggregator) =>
            aggregator.computeOutputType(inputFields(key))
              .map(key -> _)
              .leftMap(m => NonEmptyList.of(s"$key - $m"))
          }.toList.sequence.leftMap(list => s"Invalid fields: ${list.toList.mkString(", ")}")
          validationRes.map(fields => TypedObjectTypingResult(fields.toMap))
        case TypedObjectTypingResult(inputFields, _, _) =>
          Invalid(s"Fields do not match, aggregateBy: ${inputFields.keys.mkString(", ")}, aggregator: ${scalaFields.keys.mkString(", ")}")
        case _ =>
          Invalid("aggregateBy should be declared as fixed map")
      }
    }
  }

  abstract class ReducingAggregator extends Aggregator {

    override type Aggregate = Element

    override def mergeAggregates(aggregate: Aggregate, aggregate2: Aggregate): Element = addElement(aggregate, aggregate2)

    override def result(finalAggregate: Aggregate): Aggregate = finalAggregate

  }

  trait MathAggregator { self: Aggregator =>

    override def computeOutputType(input: typing.TypingResult): Validated[String, typing.TypingResult] = {
      if (input.canBeSubclassOf(Typed[Number])) {
        // In some cases type can be promoted to other class e.g. Byte is promoted to Int for sum
        Valid(promotionStrategy.promoteSingle(input))
      } else {
        Invalid(s"Invalid aggregate type: ${input.display}, should be: ${Typed[Number].display}")
      }
    }

    protected def promotionStrategy: NumberTypesPromotionStrategy

  }

}

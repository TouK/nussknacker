package pl.touk.nussknacker.engine.flink.util.transformer.aggregate
import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}
import cats.instances.list._
import scala.collection.JavaConverters._
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax

/*
  This class lists some simple aggregates
 */
object aggregates {

  private val syntax = ValidatedSyntax[String]
  import syntax._

  object MaxAggregator extends ReducingAggregator {

    override type Element = Number

    override def zero: Number = Double.MinValue

    override def addElement(n1: Number, n2: Number): Number = Math.max(n1.doubleValue(), n2.doubleValue())

    override def zeroType: TypingResult = Typed[Number]

  }

  object MinAggregator extends ReducingAggregator {

    override type Element = Number

    override def zero: Number = Double.MaxValue

    override def addElement(n1: Number, n2: Number): Number = Math.min(n1.doubleValue(), n2.doubleValue())

    override def zeroType: TypingResult = Typed[Number]
  }

  object ListAggregator extends Aggregator {

    override type Aggregate = List[AnyRef]

    override type Element = AnyRef

    override def zero: Aggregate = List()

    override def addElement(el: Element, agg: Aggregate): Aggregate = el::agg

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1 ++ agg2

    override def result(finalAggregate: Aggregate): Any = finalAggregate.asJava

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(TypedClass(classOf[java.util.List[_]], List(input)))

  }

  object SetAggregator extends Aggregator {

    override type Aggregate = Set[AnyRef]

    override type Element = AnyRef

    override def zero: Aggregate = Set()

    override def addElement(el: Element, agg: Aggregate): Aggregate = agg + el

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1 ++ agg2

    override def result(finalAggregate: Aggregate): Any = finalAggregate.asJava

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(TypedClass(classOf[java.util.Set[_]], List(input)))

  }

  object FirstAggregator extends Aggregator {

    private object Marker

    override type Aggregate = AnyRef

    override type Element = AnyRef

    override def zero: Aggregate = Marker

    override def addElement(el: Element, agg: Aggregate): Aggregate = if (agg == zero) el else agg

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1

    override def result(finalAggregate: Aggregate): Any = if (finalAggregate == zero) null else finalAggregate

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(input)

  }

  object LastAggregator extends Aggregator {

    override type Aggregate = AnyRef

    override type Element = AnyRef

    override def zero: Aggregate = null

    override def addElement(el: Element, agg: Aggregate): Aggregate = el

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg2

    override def result(finalAggregate: Aggregate): Any = finalAggregate

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(input)

  }
  
  /*
    This is more complex aggregator, as it is composed from smaller ones.
    The idea is that 
   */
  class MapAggregator(fields: java.util.Map[String, Aggregator]) extends Aggregator {

    private val scalaFields = fields.asScala.toMap

    override type Element = java.util.Map[String, Any]

    override type Aggregate = Map[String, Any]

    override val zero: Aggregate = scalaFields.mapValuesNow(_.zero)

    override def addElement(el: Element, agg: Aggregate): Aggregate = agg.map {
      case (field, value) => field -> scalaFields(field).add(el.get(field), value)
    }

    override def mergeAggregates(agg1: Aggregate, agg2: Aggregate): Aggregate = agg1.map {
      case (field, value) => field -> scalaFields(field).merge(value, agg2(field))
    }

    override def result(finalAggregate: Aggregate): Any = finalAggregate.asJava

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult] = {
      input match {
        case TypedObjectTypingResult(inputFields, klass) if inputFields.keySet == scalaFields.keySet && klass.canBeSubclassOf(Typed[java.util.Map[String, _]])=>
          val validationRes = scalaFields.map { case (key, aggregator) =>
            aggregator.computeOutputType(inputFields(key))
              .map(key -> _)
              .leftMap(m => NonEmptyList.of(s"field $key: $m"))
          }.toList.sequence.leftMap(list => s"Invalid fields: ${list.toList.mkString(", ")}")
          validationRes.map(fields => TypedObjectTypingResult(fields.toMap))
        case TypedObjectTypingResult(inputFields, _) =>
          Invalid(s"Fields do not match, aggregateBy: ${inputFields.keys.mkString(", ")}), aggregator: ${scalaFields.keys.mkString(", ")}")
        case _ =>
          Invalid("Input should be declared as fixed map")
      }
    }
  }

  trait ReducingAggregator extends Aggregator {

    override type Aggregate = Element

    override def mergeAggregates(aggregate: Aggregate, aggregate2: Aggregate): Element = addElement(aggregate, aggregate2)

    override def result(finalAggregate: Aggregate): Aggregate = finalAggregate

    def zeroType: TypingResult

    override def computeOutputType(input: typing.TypingResult): Validated[String, typing.TypingResult] = {
      if (input.canBeSubclassOf(zeroType)) {
        Valid(zeroType)
      } else {
        Invalid("Invalid type")
      }
    }

  }


}

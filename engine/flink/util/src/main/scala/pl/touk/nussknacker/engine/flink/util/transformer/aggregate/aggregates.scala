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

    override def add: (Number, Number) => Number = (n1, n2) => Math.max(n1.doubleValue(), n2.doubleValue())

    override def zeroType: TypingResult = Typed[Number]

  }

  object MinAggregator extends ReducingAggregator {

    override type Element = Number

    override def zero: Number = Double.MaxValue

    override def add: (Number, Number) => Number = (n1, n2) => Math.min(n1.doubleValue(), n2.doubleValue())

    override def zeroType: TypingResult = Typed[Number]
  }

  object ListAggregator extends Aggregator {

    override type Aggregate = List[AnyRef]

    override type Element = AnyRef

    override def zero: Aggregate = List()

    override def add: (Element, Aggregate) => Aggregate = _ :: _

    override def merge: (Aggregate, Aggregate) => Aggregate = _ ++ _

    override def result: Aggregate => Any = _.asJava

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(TypedClass(classOf[java.util.List[_]], List(input)))

  }

  object SetAggregator extends Aggregator {

    override type Aggregate = Set[AnyRef]

    override type Element = AnyRef

    override def zero: Aggregate = Set()

    override def add: (Element, Aggregate) => Aggregate = (el, agg) => agg + el

    override def merge: (Aggregate, Aggregate) => Aggregate = _ ++ _

    override def result: Aggregate => Any = _.asJava

    override def computeOutputType(input: TypingResult): Validated[String, TypingResult]
      = Valid(TypedClass(classOf[java.util.Set[_]], List(input)))

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

    override def add: (Element, Aggregate) => Aggregate = (el, ag) => ag.map {
      case (field, value) => field -> scalaFields(field).add(el.get(field), value)
    }

    override def merge: (Aggregate, Aggregate) => Aggregate = (ag1, ag2) => ag1.map {
      case (field, value) => field -> scalaFields(field).merge(value, ag2(field))
    }

    override def result: Aggregate => Any = mapAsJavaMapConverter

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

    override def merge: (Element, Element) => Element = add

    override def result: Element => Element = identity

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

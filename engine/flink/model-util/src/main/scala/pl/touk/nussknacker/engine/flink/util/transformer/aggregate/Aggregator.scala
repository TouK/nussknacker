package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.apache.flink.api.common.functions.AggregateFunction
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, NodeId}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/*
  This class serves two purposes:
  - computeOutputType provides information for validation, completion etc.
  - we provide own versions of AggregateFunction methods to use type members instead of type parameters, as it makes it easier to use
 */
abstract class Aggregator extends AggregateFunction[AnyRef, AnyRef, AnyRef] {

  type Aggregate <: AnyRef

  type Element <: AnyRef

  def zero: Aggregate

  def isNeutralForAccumulator(element: Element): Boolean = false

  def addElement(element: Element, aggregate: Aggregate): Aggregate

  def mergeAggregates(aggregate1: Aggregate, aggregate2: Aggregate): Aggregate

  def result(finalAggregate: Aggregate): AnyRef

  def alignToExpectedType(value: AnyRef, outputType: TypingResult): AnyRef = {
    value
  }

  def computeOutputType(input: TypingResult): Validated[String, TypingResult]

  override final def createAccumulator(): AnyRef = zero

  override final def add(value: AnyRef, accumulator: AnyRef): AnyRef = addElement(value.asInstanceOf[Element], accumulator.asInstanceOf[Aggregate])

  override final def getResult(accumulator: AnyRef): AnyRef = result(accumulator.asInstanceOf[Aggregate])

  override final def merge(a: AnyRef, b: AnyRef): AnyRef = mergeAggregates(a.asInstanceOf[Aggregate], b.asInstanceOf[Aggregate])

  final def toContextTransformation(variableName: String, aggregateBy: LazyParameter[_])(implicit nodeId: NodeId):
    ValidationContext => ValidatedNel[ProcessCompilationError, ValidationContext] = validationCtx => computeOutputType(aggregateBy.returnType)
    //TODO: better error?
      .leftMap(message => NonEmptyList.of(CannotCreateObjectError(message, nodeId.id)))
      .andThen(validationCtx.withVariable(variableName, _, paramName = None))
}

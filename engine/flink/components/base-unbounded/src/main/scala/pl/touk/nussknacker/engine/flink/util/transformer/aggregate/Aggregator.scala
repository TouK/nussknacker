package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.apache.flink.api.common.functions.AggregateFunction
import pl.touk.nussknacker.engine.api.{Hidden, LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher

/*
  This class serves two purposes:
  - computeOutputType provides information for validation, completion etc.
  - we provide own versions of AggregateFunction methods to use type members instead of type parameters, as it makes it easier to use
 */
abstract class Aggregator extends AggregateFunction[AnyRef, AnyRef, AnyRef] {

  type Aggregate <: AnyRef

  type Element <: AnyRef

  @Hidden
  def zero: Aggregate

  @Hidden
  def isNeutralForAccumulator(element: Element, currentAggregate: Aggregate): Boolean =
    addElement(element, currentAggregate) == currentAggregate

  @Hidden
  def addElement(element: Element, aggregate: Aggregate): Aggregate

  @Hidden
  def mergeAggregates(aggregate1: Aggregate, aggregate2: Aggregate): Aggregate

  @Hidden
  def result(finalAggregate: Aggregate): AnyRef

  @Hidden
  def alignToExpectedType(value: AnyRef, outputType: TypingResult): AnyRef = {
    value
  }

  @Hidden
  def computeOutputType(input: TypingResult): Validated[String, TypingResult]

  @Hidden
  def computeOutputTypeUnsafe(input: TypingResult): TypingResult = computeOutputType(input)
    .valueOr(e => throw new IllegalArgumentException(s"Error $e, should be handled with Validation"))

  // this can be used e.g. to compute Flink TypeInformation to store
  @Hidden
  def computeStoredType(input: TypingResult): Validated[String, TypingResult]

  @Hidden
  def computeStoredTypeUnsafe(input: TypingResult): TypingResult = computeStoredType(input)
    .valueOr(e => throw new IllegalArgumentException(s"Error $e, should be handled with Validation"))

  @Hidden
  override final def createAccumulator(): AnyRef = zero

  @Hidden
  override final def add(value: AnyRef, accumulator: AnyRef): AnyRef =
    addElement(value.asInstanceOf[Element], accumulator.asInstanceOf[Aggregate])

  @Hidden
  override final def getResult(accumulator: AnyRef): AnyRef = result(accumulator.asInstanceOf[Aggregate])

  @Hidden
  override final def merge(a: AnyRef, b: AnyRef): AnyRef =
    mergeAggregates(a.asInstanceOf[Aggregate], b.asInstanceOf[Aggregate])

  @Hidden
  final def toContextTransformation(variableName: String, emitContext: Boolean, aggregateBy: LazyParameter[_])(
      implicit nodeId: NodeId
  ): ValidationContext => ValidatedNel[ProcessCompilationError, ValidationContext] = validationCtx =>
    computeOutputType(aggregateBy.returnType)
      // TODO: better error?
      .leftMap(message => NonEmptyList.of(CannotCreateObjectError(message, nodeId.id)))
      .andThen { outputType =>
        val ctx = if (emitContext) validationCtx else validationCtx.clearVariables
        ctx.withVariable(variableName, outputType, paramName = None)
      }
      .andThen(KeyEnricher.contextTransformation)

}

package pl.touk.nussknacker.engine.process.runner

import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Service}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.process.runner.SimpleProcessConfigCreator.{
  sinkForIntsResultsHolder,
  valueMonitorResultsHolder
}

import java.net.ConnectException

class SimpleProcessConfigCreator extends EmptyProcessConfigCreator {

  override def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map(
      "logService"      -> WithCategories(LogService, "c1"),
      "throwingService" -> WithCategories(new ThrowingService(new RuntimeException("Thrown as expected")), "c1"),
      "throwingTransientService"         -> WithCategories(new ThrowingService(new ConnectException()), "c1"),
      "returningDependentTypeService"    -> WithCategories(ReturningDependentTypeService, "c1"),
      "collectingEager"                  -> WithCategories(CollectingEagerService, "c1"),
      "returningComponentUseCaseService" -> WithCategories(ReturningComponentUseCaseService, "c1")
    )

  override def sinkFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = Map(
    "monitor"      -> WithCategories(SinkFactory.noParam(MonitorEmptySink), "c2"),
    "valueMonitor" -> WithCategories(SinkForAny(valueMonitorResultsHolder), "c2"),
    "sinkForInts"  -> WithCategories.anyCategory(SinkForInts(sinkForIntsResultsHolder))
  )

  override def customStreamTransformers(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "stateCustom"                       -> WithCategories.anyCategory(StateCustomNode),
    "transformWithTime"                 -> WithCategories.anyCategory(TransformerWithTime),
    "joinBranchExpression"              -> WithCategories.anyCategory(CustomJoinUsingBranchExpressions),
    "transformerAddingComponentUseCase" -> WithCategories.anyCategory(TransformerAddingComponentUseCase)
  )

  override def sourceFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = Map(
    "input"                            -> WithCategories(simpleRecordSource(Nil), "cat2"),
    "jsonInput"                        -> WithCategories(jsonSource, "cat2"),
    "typedJsonInput"                   -> WithCategories(TypedJsonSource, "cat2"),
    "genericSourceWithCustomVariables" -> WithCategories.anyCategory(GenericSourceWithCustomVariables)
  )

}

object SimpleProcessConfigCreator extends Serializable {

  val valueMonitorResultsHolder = new TestResultsHolder[AnyRef]
  val sinkForIntsResultsHolder  = new TestResultsHolder[java.lang.Integer]

}

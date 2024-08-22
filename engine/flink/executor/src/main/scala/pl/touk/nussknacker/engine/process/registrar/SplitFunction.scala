package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.{Collector, OutputTag}
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.{EndingReference, JoinReference, NextPartReference}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar.EndId
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class SplitFunction(
    nodeToValidationCtx: Map[String, ValidationContext],
) extends ProcessFunction[InterpretationResult, Unit] {

  // we eagerly create TypeInformation here, creating it during OutputTag construction would be too expensive
  private lazy val typeInfoMap: Map[String, TypeInformation[InterpretationResult]] =
    nodeToValidationCtx.mapValuesNow(vc => InterpretationResultTypeInformation.create(vc))

  override def processElement(
      interpretationResult: InterpretationResult,
      ctx: ProcessFunction[InterpretationResult, Unit]#Context,
      out: Collector[Unit]
  ): Unit = {
    val (tagName, typeInfo) = interpretationResult.reference match {
      case NextPartReference(id) => (id, typeInfoMap(id))
      // TODO JOIN - this is a bit weird, probably refactoring of splitted process structures will help...
      case JoinReference(id, _, _) => (id, typeInfoMap(id))
      case er: EndingReference     => (EndId, typeInfoMap(er.nodeId))
    }
    ctx.output(new OutputTag[InterpretationResult](tagName, typeInfo), interpretationResult)
  }

}

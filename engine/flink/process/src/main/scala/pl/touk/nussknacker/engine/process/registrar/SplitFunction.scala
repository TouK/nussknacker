package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.{EndingReference, InterpretationResult, JoinReference, NextPartReference}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar.EndId
import pl.touk.nussknacker.engine.process.typeinformation.TypeInformationDetection

class SplitFunction(nodeToValidationCtx: Map[String, ValidationContext], typeInformationDetection: TypeInformationDetection) extends ProcessFunction[InterpretationResult, Unit] {


  //we eagerly create TypeInformation here, creating it during OutputTag construction would be too expensive
  private lazy val typeInfoMap: Map[String, TypeInformation[InterpretationResult]] =
    nodeToValidationCtx.mapValues(vc => typeInformationDetection.forInterpretationResult(vc, None))

  override def processElement(interpretationResult: InterpretationResult, ctx: ProcessFunction[InterpretationResult, Unit]#Context,
                              out: Collector[Unit]): Unit = {
    val (tagName, typeInfo) = interpretationResult.reference match {
      case NextPartReference(id) => (id, typeInfoMap(id))
      //TODO JOIN - this is a bit weird, probably refactoring of splitted process structures will help...
      case JoinReference(id, _) => (id, typeInfoMap(id))
      case er: EndingReference => (EndId, typeInfoMap(er.nodeId))
    }
    ctx.output(OutputTag[InterpretationResult](tagName)(typeInfo), interpretationResult)
  }
}

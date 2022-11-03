package pl.touk.nussknacker.engine.build

import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.graph.variable._
import pl.touk.nussknacker.engine.graph.{evaluatedparam, node}

trait GraphBuilder[R] {

  import pl.touk.nussknacker.engine.spel.Implicits._

  def creator: GraphBuilder.Creator[R]

  def build(inner: GraphBuilder.Creator[R]): GraphBuilder[R]

  def source(id: String, typ: String, params: (String, Expression)*): GraphBuilder[SourceNode] =
    new SimpleGraphBuilder(SourceNode(Source(id, SourceRef(typ, params.map(evaluatedparam.Parameter.tupled).toList)), _))

  def buildVariable(id: String, varName: String, fields: (String, Expression)*): GraphBuilder[R] =
    build(node => creator(OneOutputSubsequentNode(VariableBuilder(id, varName, fields.map(f => Field(f._1, f._2)).toList), node)))

  def buildSimpleVariable(id: String, varName: String, value: Expression): GraphBuilder[R] =
    build(node => creator(OneOutputSubsequentNode(Variable(id, varName, value), node)))

  def enricher(id: String, output: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node => creator(OneOutputSubsequentNode(Enricher(id, ServiceRef(svcId, params.map(Parameter.tupled).toList), output), node)))

  def processor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node => creator(OneOutputSubsequentNode(Processor(id, ServiceRef(svcId, params.map(Parameter.tupled).toList)), node)))

  def disabledProcessor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node => creator(OneOutputSubsequentNode(Processor(id, ServiceRef(svcId, params.map(Parameter.tupled).toList), isDisabled = Some(true)), node)))

  def subprocessOneOut(id: String, subProcessId: String, fragmentOutputDefinitionName: String, outputParamName: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node => creator(SubprocessNode(SubprocessInput(id, SubprocessRef(subProcessId, params.map(Parameter.tupled).toList, Map(fragmentOutputDefinitionName -> outputParamName))), Map(fragmentOutputDefinitionName -> node))))

  def subprocess(id: String, subProcessId: String, params: List[(String, Expression)], outputParameters: Map[String, String], outputs: Map[String, SubsequentNode]): R =
    creator(SubprocessNode(SubprocessInput(id, SubprocessRef(subProcessId, params.map(Parameter.tupled), outputParameters)), outputs))

  def subprocessEnd(id: String, subProcessId: String, params: (String, Expression)*): R =
    creator(SubprocessNode(SubprocessInput(id, SubprocessRef(subProcessId, params.map(Parameter.tupled).toList, Map.empty)), Map()))

  def fragmentInput(id: String, params: (String, Class[_])*): GraphBuilder[SourceNode] =
    new SimpleGraphBuilder(SourceNode(SubprocessInputDefinition(id, params.map(kv => SubprocessParameter(kv._1, SubprocessClazzRef(kv._2.getName))).toList), _))

  def fragmentOutput(id: String, outputName: String, params: (String, Expression)*): R =
    creator(EndingNode(SubprocessOutputDefinition(id, outputName, params.map(kv => Field(kv._1, kv._2)).toList )))

  def filter(id: String, expression: Expression, disabled: Option[Boolean] = None): GraphBuilder[R] =
    build(node => creator(FilterNode(Filter(id, expression, disabled), node, None)))

  def filter(id: String, expression: Expression, nextFalse: SubsequentNode): GraphBuilder[R] =
    build(node => creator(FilterNode(Filter(id, expression), node, Some(nextFalse))))

  def emptySink(id: String, typ: String, params: (String, Expression)*): R =
    creator(EndingNode(Sink(id, SinkRef(typ, params.map(evaluatedparam.Parameter.tupled).toList))))

  def disabledSink(id: String, typ: String): R =
      creator(EndingNode(Sink(id, SinkRef(typ, List()), isDisabled = Some(true))))

  def processorEnd(id: String, svcId: String, params: (String, Expression)*): R =
    creator(EndingNode(Processor(id, ServiceRef(svcId, params.map(Parameter.tupled).toList))))

  def disabledProcessorEnd(id: String, svcId: String, params: (String, Expression)*): R =
    creator(EndingNode(Processor(id, ServiceRef(svcId, params.map(Parameter.tupled).toList), isDisabled = Some(true))))

  def branchEnd(branchId: String, joinId: String): R =
    creator(BranchEnd(node.BranchEndData(BranchEndDefinition(branchId, joinId))))

  def switch(id: String, nexts: Case*): R =
    creator(SwitchNode(Switch(id), nexts.toList, None))

  def switch(id: String, expression: Expression, exprVal: String, nexts: Case*): R =
    creator(SwitchNode(Switch(id, Some(expression), Some(exprVal)), nexts.toList, None))

  def switch(id: String, expression: Expression, exprVal: String,
             defaultNext: SubsequentNode, nexts: Case*): R =
    creator(SwitchNode(Switch(id, Some(expression), Some(exprVal)), nexts.toList, Some(defaultNext)))

  def customNode(id: String, outputVar: String, customNodeRef: String, params: (String, Expression)*): GraphBuilder[R]  =
    build(node => creator(OneOutputSubsequentNode(CustomNode(id, Some(outputVar), customNodeRef, params.map(Parameter.tupled).toList), node)))

  // outputVar must be provided always when parameter with @OutputVariableName annotation is used - look into comment in @OutputVariableName
  def endingCustomNode(id: String, outputVar: Option[String], customNodeRef: String, params: (String, Expression)*): R  =
    creator(EndingNode(CustomNode(id, outputVar, customNodeRef, params.map(Parameter.tupled).toList)))

  def customNodeNoOutput(id: String, customNodeRef: String, params: (String, Expression)*): GraphBuilder[R]  =
    build(node => creator(OneOutputSubsequentNode(CustomNode(id, None, customNodeRef, params.map(Parameter.tupled).toList), node)))


  def split(id: String, nexts: SubsequentNode*): R = creator(SplitNode(Split(id), nexts.toList))

  def to(node: SubsequentNode): R =
    creator(node)

  def join(id: String, typ: String, output: Option[String], branchParams: List[(String, List[(String, Expression)])], params: (String, Expression)*): GraphBuilder[SourceNode] = {
    val parameters = params.map(evaluatedparam.Parameter.tupled)
    val branchParameters = branchParams.map {
      case (branchId, bParams) =>
        BranchParameters(branchId, bParams.map(evaluatedparam.Parameter.tupled))
    }
    new SimpleGraphBuilder(SourceNode(node.Join(id, output, typ, parameters.toList, branchParameters), _))
  }
}

private[build] class SimpleGraphBuilder[R<:Node](val creator: GraphBuilder.Creator[R]) extends GraphBuilder[R] {
  override def build(inner: GraphBuilder.Creator[R]) = new SimpleGraphBuilder(inner)
}

object GraphBuilder extends GraphBuilder[SubsequentNode] {

  type Creator[R] = SubsequentNode => R

  override def creator: Creator[SubsequentNode] = identity[SubsequentNode]

  override def build(inner: Creator[SubsequentNode]) = new SimpleGraphBuilder[SubsequentNode](inner)

}

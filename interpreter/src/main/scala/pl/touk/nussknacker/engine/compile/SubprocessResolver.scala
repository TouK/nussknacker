package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid, invalidNel, valid}
import cats.data._
import cats.implicits._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.definition.{SubprocessDefinition, SubprocessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef

object SubprocessResolver {

  // For easier testing purpose
  def apply(subprocesses: Iterable[CanonicalProcess],
            componentsConfig: Map[String, SingleComponentConfig] = Map.empty,
            classLoader: ClassLoader = SubprocessResolver.getClass.getClassLoader): SubprocessResolver = {
    val subprocessMap = subprocesses.map(a => a.metaData.id -> a).toMap
    val definitionExtractor = new SubprocessDefinitionExtractor(componentsConfig.get, classLoader)
    SubprocessResolver(subprocessMap.get _, definitionExtractor)
  }

}

case class SubprocessResolver(subprocesses: String => Option[CanonicalProcess], definitionExtractor: SubprocessDefinitionExtractor) {

  type CompilationValid[A] = ValidatedNel[ProcessCompilationError, A]

  type CanonicalBranch = List[CanonicalNode]

  type ValidatedWithBranches[T] = WriterT[CompilationValid, List[CanonicalBranch], T]

  private def additionalApply[T](value: CompilationValid[T]): ValidatedWithBranches[T] = WriterT[CompilationValid, List[CanonicalBranch], T](value.map((Nil, _)))

  private def validBranches[T](value: T): ValidatedWithBranches[T] = WriterT[CompilationValid, List[CanonicalBranch], T](Valid(Nil, value))

  private def invalidBranches[T](value: ProcessCompilationError): ValidatedWithBranches[T] = WriterT[CompilationValid, List[CanonicalBranch], T](Invalid(NonEmptyList.of(value)))

  private implicit class RichValidatedWithBranches[T](value: ValidatedWithBranches[T]) {
    def andThen[Y](fun: T => ValidatedWithBranches[Y]): ValidatedWithBranches[Y] = {
      WriterT[CompilationValid, List[CanonicalBranch], Y](value.run.andThen { case (additional, iValue) =>
        val result = fun(iValue)
        result.mapWritten(additional ++ _).run
      })
    }
  }

  def resolve(canonicalProcess: CanonicalProcess): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val output: ValidatedWithBranches[NonEmptyList[CanonicalBranch]] = canonicalProcess.allStartNodes.map(resolveCanonical(Nil)).sequence
    //we unwrap result and put it back to canonical process
    output.run.map { case (additional, original) =>
      val allBranches = additional.foldLeft(original)(_.append(_))
      canonicalProcess.withNodes(allBranches)
    }
  }

  private def resolveCanonical(idPrefix: List[String]): CanonicalBranch => ValidatedWithBranches[CanonicalBranch] = {
    iterateOverCanonicals({
      case canonicalnode.Subprocess(SubprocessInput(dataId, _, _, Some(true), _), nextNodes) if nextNodes.values.size > 1 =>
        invalidBranches(DisablingManyOutputsSubprocess(dataId, nextNodes.keySet))
      case canonicalnode.Subprocess(SubprocessInput(dataId, _, _, Some(true), _), nextNodes) if nextNodes.values.isEmpty =>
        invalidBranches(DisablingNoOutputsSubprocess(dataId))
      case canonicalnode.Subprocess(data@SubprocessInput(_, _, _, Some(true), _), nextNodesMap) =>
        //TODO: disabling nodes should be in one place
        val output = nextNodesMap.keys.head
        resolveCanonical(idPrefix)(nextNodesMap.values.head).map { resolvedNexts =>
          val outputId = s"${NodeDataFun.nodeIdPrefix(idPrefix)(data).id}-$output"
          FlatNode(NodeDataFun.nodeIdPrefix(idPrefix)(data)) :: FlatNode(SubprocessUsageOutput(outputId, output, None, None)) :: resolvedNexts
        }
      //here is the only interesting part - not disabled subprocess
      case canonicalnode.Subprocess(subprocessInput: SubprocessInput, nextNodes) =>

        //subprocessNodes contain Input
        additionalApply(resolveInput(subprocessInput)).andThen { definition =>
          //we resolve what follows after subprocess, and all its branches
          val nextResolvedV = nextNodes.map { case (k, v) =>
            resolveCanonical(idPrefix)(v).map((k, _))
          }.toList.sequence[ValidatedWithBranches, (String, List[CanonicalNode])].map(_.toMap)

          val subResolvedV = resolveCanonical(idPrefix :+ subprocessInput.id)(definition.nodes)
          val additionalResolved = definition.additionalBranches.map(resolveCanonical(idPrefix :+ subprocessInput.id)).sequence

          //we replace subprocess outputs with following nodes from parent process
          val nexts = (nextResolvedV, subResolvedV, additionalResolved, additionalApply(definition.validOutputs(NodeId(subprocessInput.id))))
            .mapN { (nodeResolved, nextResolved, additionalResolved, _) => (replaceCanonicalList(nodeResolved, subprocessInput.id, subprocessInput.ref.outputVariableNames), nextResolved, additionalResolved) }
            .andThen { case (replacement, nextResolved, additionalResolved) =>
              additionalResolved.map(replacement).sequence.andThen { resolvedAdditional =>
                replacement(nextResolved).mapWritten(_ ++ resolvedAdditional)
              }
            }
          // now, this is a bit dirty trick - we pass subprocess parameter types to subprocessInput node to handle parameter types by interpreter
          // we can't just change subprocessParams type to List[Parameter] because Parameter is not a serializable, scenario-api accessible class
          nexts.map { replaced =>
            val withParametersForInterpreter = subprocessInput.copy(subprocessParams = Some(definition.subprocessParameters))
            FlatNode(NodeDataFun.nodeIdPrefix(idPrefix)(withParametersForInterpreter)) :: replaced
          }
        }
    }, NodeDataFun.nodeIdPrefix(idPrefix))
  }

  def resolveInput(subprocessInput: SubprocessInput): CompilationValid[SubprocessDefinition] = {
    implicit val nodeId: NodeId = NodeId(subprocessInput.id)
    subprocesses.apply(subprocessInput.ref.id)
      .map(valid).getOrElse(invalidNel(UnknownSubprocess(id = subprocessInput.ref.id, nodeId = nodeId.id)))
      .map(definitionExtractor.extractSubprocessDefinition)
  }

  //we replace outputs in subprocess with part of parent process
  private def replaceCanonicalList(replacement: Map[String, CanonicalBranch], parentId: String, outputs: Map[String, String]): CanonicalBranch => ValidatedWithBranches[CanonicalBranch] = {
    iterateOverCanonicals({
      case FlatNode(SubprocessOutputDefinition(id, name, fields, add)) => {
        replacement.get(name) match {
          case Some(nodes) if fields.isEmpty => validBranches(FlatNode(SubprocessUsageOutput(id, name, None, add)) :: nodes)
          case Some(nodes) =>
            val outputName = outputs.getOrElse(name, default = name) // when no `outputVariableName` defined we use output name from fragment as variable name
            validBranches(FlatNode(SubprocessUsageOutput(id, name, Some(SubprocessOutputVarDefinition(outputName, fields)), add)) :: nodes)
          case _ => invalidBranches(FragmentOutputNotDefined(name, Set(id, parentId)))
        }
      }
    }, NodeDataFun.id)
  }

  //kind "flatMap" for branches
  private def iterateOverCanonicals(action: PartialFunction[CanonicalNode, ValidatedWithBranches[CanonicalBranch]], dataAction: NodeDataFun): CanonicalBranch => ValidatedWithBranches[CanonicalBranch] =
    (l: List[CanonicalNode]) => l.map(iterateOverCanonical(action, dataAction)).sequence[ValidatedWithBranches, CanonicalBranch].map(_.flatten)

  //lifts partial action to total function with defult actions
  private def iterateOverCanonical(action: PartialFunction[CanonicalNode, ValidatedWithBranches[CanonicalBranch]],
                                   dataAction: NodeDataFun): CanonicalNode => ValidatedWithBranches[CanonicalBranch] = cnode => {
    val listFun = iterateOverCanonicals(action, dataAction)
    action.applyOrElse[CanonicalNode, ValidatedWithBranches[CanonicalBranch]](cnode, {
      case FlatNode(data) => validBranches(List(FlatNode(dataAction(data))))
      case canonicalnode.FilterNode(data, nextFalse) =>
        listFun(nextFalse).map(canonicalnode.FilterNode(dataAction(data), _)).map(List(_))
      case canonicalnode.SplitNode(data, nexts) =>
        nexts.map(listFun).sequence[ValidatedWithBranches, List[CanonicalNode]]
          .map(canonicalnode.SplitNode(dataAction(data), _)).map(List(_))
      case canonicalnode.SwitchNode(data, nexts, defaultNext) =>
        (
          nexts.map(cas => listFun(cas.nodes).map(replaced => canonicalnode.Case(cas.expression, replaced))).sequence[ValidatedWithBranches, canonicalnode.Case],
          listFun(defaultNext)
        ).mapN { (resolvedCases, resolvedDefault) =>
          List(canonicalnode.SwitchNode(dataAction(data), resolvedCases, resolvedDefault))
        }
      case canonicalnode.Subprocess(data, nodes) =>
        nodes.map { case (k, v) =>
          listFun(v).map(k -> _)
        }.toList.sequence[ValidatedWithBranches, (String, List[CanonicalNode])].map(replaced => List(canonicalnode.Subprocess(dataAction(data), replaced.toMap)))
    })
  }

  trait NodeDataFun {
    def apply[T <: NodeData](n: T): T
  }

  object NodeDataFun {
    val id: NodeDataFun = new NodeDataFun {
      override def apply[T <: NodeData](n: T): T = n
    }

    def nodeIdPrefix(prefix: List[String]): NodeDataFun = new NodeDataFun {
      override def apply[T <: NodeData](n: T): T = prefixNodeId(prefix, n)
    }
  }

  private def prefixNodeId[T <: NodeData](prefix: List[String], nodeData: T): T = {
    import pl.touk.nussknacker.engine.util.copySyntax._
    def prefixId(id: String): String = (prefix :+ id).mkString("-")
    //this casting is weird, but we want to have both exhaustiveness check and GADT behaviour with copy syntax...
    (nodeData.asInstanceOf[NodeData] match {
      case e: RealNodeData =>
        e.copy(id = prefixId(e.id))
      case BranchEndData(BranchEndDefinition(id, joinId)) =>
        BranchEndData(BranchEndDefinition(id, prefixId(joinId)))
    }).asInstanceOf[T]
  }

}

case class Output(name: String, nonEmptyFields: Boolean)

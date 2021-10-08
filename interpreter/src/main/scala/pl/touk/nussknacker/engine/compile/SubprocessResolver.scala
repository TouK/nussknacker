package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}

object SubprocessResolver {
  def apply(subprocesses: Iterable[CanonicalProcess]): SubprocessResolver =
    SubprocessResolver(subprocesses.map(a => a.metaData.id -> a).toMap)
}

case class SubprocessResolver(subprocesses: Map[String, CanonicalProcess]) {

  type CompilationValid[A] = ValidatedNel[ProcessCompilationError, A]

  type CanonicalBranch = List[CanonicalNode]

  type WithAdditionalBranches[T] = Writer[List[CanonicalBranch], T]

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
          FlatNode(NodeDataFun.nodeIdPrefix(idPrefix)(data)) :: FlatNode(SubprocessOutput(outputId, output, List.empty, None)) :: resolvedNexts
        }
      //here is the only interesting part - not disabled subprocess
      case canonicalnode.Subprocess(subprocessInput: SubprocessInput, nextNodes) =>

        initialSubprocessChecks(subprocessInput).andThen { case (parameters, subprocessNodes, subprocessAdditionalBranches) =>
          //we resolve what follows after subprocess, and all its branches
          val nextResolvedV = nextNodes.map { case (k, v) =>
            resolveCanonical(idPrefix)(v).map((k, _))
          }.toList.sequence[ValidatedWithBranches, (String, List[CanonicalNode])].map(_.toMap)

          val subResolvedV = resolveCanonical(idPrefix :+ subprocessInput.id)(subprocessNodes)
          val additionalResolved = subprocessAdditionalBranches.map(resolveCanonical(idPrefix :+ subprocessInput.id)).sequence


          //we replace subprocess outputs with following nodes from parent process
          val nexts = (nextResolvedV, subResolvedV, additionalResolved)
            .mapN { (nodeResolved, nextResolved, additionalResolved) => (replaceCanonicalList(nodeResolved, subprocessInput.id), nextResolved, additionalResolved) }
            .andThen { case (replacement, nextResolved, additionalResolved) =>
              additionalResolved.map(replacement).sequence.andThen { resolvedAdditional =>
                replacement(nextResolved).mapWritten(_ ++ resolvedAdditional)
              }
            }
          //now, this is a bit dirty trick - we pass subprocess parameter types to subprocessInput node to enable proper validation etc.
          nexts.map(replaced => FlatNode(NodeDataFun.nodeIdPrefix(idPrefix)(subprocessInput.copy(subprocessParams = Some(parameters)))) :: replaced)
        }
    }, NodeDataFun.nodeIdPrefix(idPrefix))
  }

  //we do initial validation of existence of subprocess, its parameters and we extract all branches
  private def initialSubprocessChecks(subprocessInput: SubprocessInput): ValidatedWithBranches[(List[SubprocessInputDefinition.SubprocessParameter], CanonicalBranch, List[CanonicalBranch])] = {
    additionalApply(Validated.fromOption(subprocesses.get(subprocessInput.ref.id), NonEmptyList.of(UnknownSubprocess(id = subprocessInput.ref.id, nodeId = subprocessInput.id)))
      .andThen { subprocess =>
        val additionalBranches = subprocess.allStartNodes.collect {
          case a@FlatNode(_: Join) :: _ => a
        }
        Validated.fromOption(subprocess.allStartNodes.collectFirst {
          case FlatNode(SubprocessInputDefinition(_, parameters, _)) :: nodes => (parameters, nodes, additionalBranches)
        }, NonEmptyList.of(UnknownSubprocess(id = subprocessInput.ref.id, nodeId = subprocessInput.id)))
      }).andThen { case (parameters, nodes, additionalBranches) =>
      checkProcessParameters(subprocessInput.ref, parameters.map(_.name), subprocessInput.id).map { _ =>
        (parameters, nodes, additionalBranches)
      }
    }
  }

  private def checkProcessParameters(ref: SubprocessRef, parameters: List[String], nodeId: String): ValidatedWithBranches[Unit] = {
    val results = Validations.validateSubProcessParameters[ProcessCompilationError](parameters.toSet, ref.parameters.map(_.name).toSet)(NodeId(nodeId))
    WriterT[CompilationValid, List[CanonicalBranch], Unit](results.map((Nil, _)))
  }

  //we replace outputs in subprocess with part of parent process
  private def replaceCanonicalList(replacement: Map[String, CanonicalBranch], parentId: String): CanonicalBranch => ValidatedWithBranches[CanonicalBranch] = {
    iterateOverCanonicals({
      case FlatNode(SubprocessOutputDefinition(id, name, fields, add)) => replacement.get(name) match {
        case Some(nodes) => validBranches(FlatNode(SubprocessOutput(id, name, fields, add)) :: nodes)
        case None => invalidBranches(UnknownSubprocessOutput(name, Set(id, parentId)))
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
      override def apply[T <: NodeData](n: T): T = {
        pl.touk.nussknacker.engine.util.node.prefixNodeId(prefix, n)
      }
    }
  }

}

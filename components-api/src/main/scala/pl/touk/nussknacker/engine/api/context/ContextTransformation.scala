package pl.touk.nussknacker.engine.api.context

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError

/**
  * Wrapper for tuple of definition and implementation of variable context transformation
  * @param definition Definition of variable context transformation - defines how will look ValidationContext
  *                   (types of variables) after transformation in runtime
  * @param implementation Implements real variable context transformation which was defined in definition
  *                       Returned type depends on execution engine. It should be lazy evaluated to make sure that
  *                       none runtime work will be run in compilation/validation stage
  */
case class ContextTransformation(definition: ContextTransformationDef, implementation: Any)
    extends AbstractContextTransformation {
  override type ContextTransformationDefType = ContextTransformationDef
}

case class JoinContextTransformation(definition: JoinContextTransformationDef, implementation: Any)
    extends AbstractContextTransformation {
  override type ContextTransformationDefType = JoinContextTransformationDef
}

sealed trait AbstractContextTransformation {

  type ContextTransformationDefType <: AbstractContextTransformationDef

  def definition: ContextTransformationDefType

  // Should be lazy evaluated to be sure that none runtime work will be run in compilation/validation stage
  // The result of evaluation depends on execution engine
  def implementation: Any

}

/**
  * Set of builders for ContextTransformation e.g.
  * `
  *   ContextTransformation
  *     .definedBy(_.withVariable("foo", Typed[String])
  *     .implementedBy { () =>
  *       Future.success(Context.withRandomId.withVariable("foo", "bar")
  *     }
  * `
  */
object ContextTransformation {

  // Helper in case when you want to use branch name (node id) as a variable/field
  def sanitizeBranchName(branchId: String): String =
    branchId.toCharArray.zipWithIndex.collect {
      case (a, 0) if Character.isJavaIdentifierStart(a)  => a
      case (a, 0) if !Character.isJavaIdentifierStart(a) => "_"
      case (a, _) if Character.isJavaIdentifierPart(a)   => a
      case (a, _) if !Character.isJavaIdentifierPart(a)  => "_"
    }.mkString

  def checkNotAllowedNodeNames(nodeIds: List[String], notAllowedNames: Set[String])(
      implicit nodeId: NodeId
  ): List[ProcessCompilationError] = {
    val sanitizedNotAllowedNames = notAllowedNames.map(sanitizeBranchName)
    nodeIds.flatMap(x => {
      if (sanitizedNotAllowedNames.contains(sanitizeBranchName(x))) {
        List(CustomNodeError(s"""Input node can not be named "$x"""", None))
      } else {
        List()
      }
    })
  }

  def checkIdenticalSanitizedNodeNames(nodeIds: List[String])(implicit nodeId: NodeId): List[ProcessCompilationError] =
    nodeIds
      .groupBy(sanitizeBranchName)
      .flatMap {
        case (_, values) if values.size >= 2 =>
          val namesList = values.map("\"" + _ + "\"").mkString(", ")
          List(ProcessCompilationError.CustomNodeError(s"Nodes $namesList have too similar names", None))
        case _ =>
          List()
      }
      .toList

  def findUniqueParentContext(
      contextMap: Map[String, ValidationContext]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Option[ValidationContext]] = {
    contextMap.values.map(_.parent).toList.distinct match {
      case Nil      => Valid(None)
      case a :: Nil => Valid(a)
      case more => Invalid(NonEmptyList.of(CustomNodeError(nodeId.id, s"Not consistent parent contexts: $more", None)))
    }
  }

  def join: JoinBuilder = new JoinBuilder

  def definedBy(definition: ContextTransformationDef): DefinedByBuilder =
    new DefinedByBuilder(definition)

  def definedBy(
      transformContext: ValidationContext => ValidatedNel[ProcessCompilationError, ValidationContext]
  ): DefinedByBuilder =
    new DefinedByBuilder(new ContextTransformationDef {
      override def transform(context: ValidationContext): ValidatedNel[ProcessCompilationError, ValidationContext] =
        transformContext(context)
    })

  class DefinedByBuilder(definition: ContextTransformationDef) {
    def implementedBy(implementation: Any): ContextTransformation =
      ContextTransformation(definition, implementation)
  }

  class JoinBuilder {
    def definedBy(definition: JoinContextTransformationDef): JoinDefinedByBuilder =
      new JoinDefinedByBuilder(definition)

    def definedBy(
        transformContexts: Map[String, ValidationContext] => ValidatedNel[ProcessCompilationError, ValidationContext]
    ): JoinDefinedByBuilder =
      new JoinDefinedByBuilder(new JoinContextTransformationDef {

        override def transform(
            contextByBranchId: Map[String, ValidationContext]
        ): ValidatedNel[ProcessCompilationError, ValidationContext] =
          transformContexts(contextByBranchId)

      })

  }

  class JoinDefinedByBuilder(definition: JoinContextTransformationDef) {
    def implementedBy(implementation: Any): JoinContextTransformation =
      JoinContextTransformation(definition, implementation)
  }

}

sealed trait AbstractContextTransformationDef {

  type InputContext

  def transform(context: InputContext): ValidatedNel[ProcessCompilationError, ValidationContext]

}

trait ContextTransformationDef extends AbstractContextTransformationDef {

  override final type InputContext = ValidationContext

  def andThen(nextTransformation: ContextTransformationDef): ContextTransformationDef =
    new ContextTransformationDef {
      override def transform(context: ValidationContext): ValidatedNel[ProcessCompilationError, ValidationContext] =
        ContextTransformationDef.this.transform(context).andThen(nextTransformation.transform)
    }

}

trait JoinContextTransformationDef extends AbstractContextTransformationDef {

  // branchId -> ValidationContext
  override final type InputContext = Map[String, ValidationContext]

  def andThen(nextTransformation: ContextTransformationDef): JoinContextTransformationDef =
    new JoinContextTransformationDef {

      override def transform(
          contextByBranchId: Map[String, ValidationContext]
      ): ValidatedNel[ProcessCompilationError, ValidationContext] =
        JoinContextTransformationDef.this.transform(contextByBranchId).andThen(nextTransformation.transform)

    }

}

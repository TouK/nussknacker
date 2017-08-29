package pl.touk.nussknacker.engine.spel

import cats.data.Validated
import org.springframework.expression.Expression
import org.springframework.expression.spel.ast._
import org.springframework.expression.spel.{SpelNode, standard}
import pl.touk.nussknacker.engine.compile.{ValidatedSyntax, ValidationContext}
import pl.touk.nussknacker.engine.compiledgraph.expression
import pl.touk.nussknacker.engine.compiledgraph.expression.ExpressionParseError
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ClazzRef

class SpelExpressionValidator(expr: Expression, ctx: ValidationContext) {
  import SpelExpressionValidator._
  import cats.instances.list._

  private val syntax = ValidatedSyntax[ExpressionParseError]
  import syntax._

  private val ignoredTypes: Set[ClazzRef] = Set(
    classOf[java.util.Map[_, _]],
    classOf[scala.collection.convert.Wrappers.MapWrapper[_, _]],
    classOf[Any],
    classOf[Object],
    classOf[AnyRef]
  ).map(ClazzRef.apply)

  def validate(): Validated[ExpressionParseError, Expression] = {
    val ast = expr.asInstanceOf[standard.SpelExpression].getAST
    resolveReferences(ast).andThen { _ =>
      findAllPropertyAccess(ast, None, None).andThen { propertyAccesses =>
        propertyAccesses.flatMap(validatePropertyAccess).headOption match {
          case Some(error) => Validated.invalid(error)
          case None => Validated.valid(expr)
        }
      }
    }
  }

  private def resolveReferences(node: SpelNode): Validated[ExpressionParseError, Expression] = {
    val references = findAllVariableReferences(node)
    val notResolved = references.filterNot(ctx.contains)
    if (notResolved.isEmpty) Validated.valid(expr)
    else Validated.Invalid(ExpressionParseError(s"Unresolved references ${notResolved.mkString(", ")}"))
  }

  private def findAllPropertyAccess(n: SpelNode, rootClass: Option[ClazzRef], parent: Option[SpelNode]): Validated[ExpressionParseError, List[SpelPropertyAccess]] = {
    n match {
      case ce: CompoundExpression if ce.childrenHead.isInstanceOf[VariableReference] =>
        val childrenTail = ce.children.tail.toList
        val references = findVariableReferenceAccess(ce.childrenHead.asInstanceOf[VariableReference], childrenTail)
        //TODO: is everything ok with it??
        if (childrenTail.headOption.exists(_.isInstanceOf[MethodReference])) {
          references.andThen(_ => validateChildren(childrenTail, rootClass, Some(n)))
        } else {
          references
        }
      //TODO: variables validation in Projection/Selection
      case ce: CompoundExpression if ce.childrenHead.isInstanceOf[PropertyOrFieldReference] =>
        Validated.invalid(ExpressionParseError(s"Non reference '${ce.childrenHead.toStringAST}' occurred. Maybe you missed '#' in front of it?"))
      case prop: PropertyOrFieldReference if (rootClass.isEmpty && parent.exists(par => par.isInstanceOf[MethodReference] || par.isInstanceOf[Operator])) || parent.isEmpty =>
        Validated.invalid(ExpressionParseError(s"Non reference '${prop.toStringAST}' occurred. Maybe you missed '#' in front of it?"))
      //TODO: variables validation in Projection/Selection, but we need to know type of list content first...
      case prop: Projection =>
        validateChildren(n.children, Some(ClazzRef(classOf[Object])), Some(n))
      case sel: Selection =>
        validateChildren(n.children, Some(ClazzRef(classOf[Object])), Some(n))
      case map: InlineMap =>
        //we take only odd indices, even ones are map keys...
        validateChildren(n.children.zipWithIndex.filter(_._2 % 2 == 1).map(_._1), None, Some(n))
      case other =>
        validateChildren(n.children, None, Some(n))
    }
  }

  private def validateChildren(children: Seq[SpelNode], rootClass: Option[ClazzRef], parent: Option[SpelNode]): Validated[expression.ExpressionParseError, List[SpelPropertyAccess]] = {
    val accessesWithErrors = children.toList.map { child => findAllPropertyAccess(child, rootClass, parent).toValidatedNel }.sequence
    accessesWithErrors.map(_.flatten).leftMap(_.head)
  }

  private def findVariableReferenceAccess(reference: VariableReference, children: List[SpelNode] = List()) = {
    val variableName = reference.toStringAST.tail
    val references = children.takeWhile(_.isInstanceOf[PropertyOrFieldReference]).map(_.toStringAST) //we don't validate all of children which is not correct in all cases i.e `#obj.children.?[id == '55'].empty`
    val clazzRef = ctx.apply(variableName)
    if (ignoredTypes.contains(clazzRef)) Validated.valid(List.empty) //we skip validation in case of SpEL Maps and when we are not sure about type
    else {
      Validated.valid(List(SpelPropertyAccess(variableName, references, ctx.apply(variableName))))
    }
  }


  private def validatePropertyAccess(propAccess: SpelPropertyAccess): Option[ExpressionParseError] = {
    def checkIfPropertiesExistsOnClass(propsToGo: List[String], clazz: ClazzRef): Option[ExpressionParseError] = {
      if (propsToGo.isEmpty) None
      else {
        val currentProp = propsToGo.head
        val typeInfo = ctx.getTypeInfo(clazz)
        typeInfo.getMethod(currentProp) match {
          case Some(anotherClazzRef) =>
            checkIfPropertiesExistsOnClass(propsToGo.tail, anotherClazzRef)
          case None =>
            Some(ExpressionParseError(s"There is no property '$currentProp' in type '${typeInfo.clazzName.refClazzName}'"))
        }
      }
    }
    checkIfPropertiesExistsOnClass(propAccess.properties, propAccess.clazz)
  }

  private def findAllVariableReferences(n: SpelNode): List[String] = {
    if (n.getChildCount == 0) {
      n match {
        case vr: VariableReference => List(vr.toStringAST.tail)
        case _ => List()
      }
    }
    else n.children.flatMap(findAllVariableReferences).toList
  }

}

object SpelExpressionValidator {

  implicit class RichSpelNode(n: SpelNode) {
    def children: Seq[SpelNode] = {
      (0 until n.getChildCount).map(i => n.getChild(i))
    }
    def childrenHead: SpelNode = {
      n.getChild(0)
    }

  }

  case class SpelPropertyAccess(variable: String, properties: List[String], clazz: ClazzRef)
}
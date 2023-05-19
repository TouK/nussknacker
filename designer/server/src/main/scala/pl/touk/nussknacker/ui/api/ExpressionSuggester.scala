package pl.touk.nussknacker.ui.api

import io.circe.generic.JsonCodec
import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast.{PropertyOrFieldReference, StringLiteral, VariableReference}
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

import scala.collection.Map

class ExpressionSuggester {

  private lazy val parser = new org.springframework.expression.spel.standard.SpelExpressionParser

  def expressionSuggestions(expression: Expression, caretPosition2d: CaretPosition2d, variables: Map[String, TypingResult], typeDefinitions: TypeDefinitionSet): List[ExpressionSuggestion] = {
    // currently we only support Spel expressions
    if(expression.language != Language.Spel) {
      return Nil
    }
    val spelExpression = expression.expression
    val normalizedCaret = normalizedCaretPosition(spelExpression, caretPosition2d)
    if (normalizedCaret == 0) {
      return Nil
    }
    val previousChar = spelExpression.substring(normalizedCaret - 1, normalizedCaret)
    val shouldInsertDummyVariable = (previousChar == "#" || previousChar == ".") && (normalizedCaret == spelExpression.length || !spelExpression.charAt(normalizedCaret).isLetter)
    val input = if (shouldInsertDummyVariable) {
      insertDummyVariable(spelExpression, normalizedCaret)
    } else {
      spelExpression
    }
    val ast = parser.parseRaw(input).getAST
    val nodeInPosition = findNodeInPosition(ast, normalizedCaret)

    def filterMapByName[V](map: Map[String, V], name: String): Map[String, V] = {
      if (shouldInsertDummyVariable) {
        map
      } else {
        map.filter { case (key, _) => key.toLowerCase.contains(name.toLowerCase) }
      }
    }

    nodeInPosition match {
      case Some(node) => node match {
        case v: VariableReference =>
          val filteredVariables = filterMapByName(variables, v.toStringAST.stripPrefix("#"))
          filteredVariables.toList.map { case (variable, clazzRef) => ExpressionSuggestion(s"#$variable", clazzRef, fromClass = false) }
        case p: PropertyOrFieldReference =>
          //TODO: this solution only looks for first previous node, so it works for single nested expression, like #var.field
          val prevNode = findPrevNodeInPosition(ast, p.getStartPosition)

          def filterClassMethods(classDefinition: ClazzDefinition): List[ExpressionSuggestion] = {
            val methods = filterMapByName(classDefinition.methods, p.getName)

            methods.values.flatten
              .map(m => ExpressionSuggestion(m.name, m.signatures.head.result, fromClass = false))
              .toList
          }

          val prevNodeTypingResult = prevNode.flatMap {
            case _: StringLiteral => Some(Typed[String])
            case n => variables.get(n.toStringAST.stripPrefix("#"))
          }

          prevNodeTypingResult.map {
            case tc: TypedClass => typeDefinitions.get(tc.klass).map(filterClassMethods).getOrElse(Nil)
            case to: TypedObjectTypingResult => filterMapByName(to.fields, p.getName).toList.map { case (methodName, clazzRef) => ExpressionSuggestion(methodName, clazzRef, fromClass = false) }
            case _ => Nil
          }.getOrElse(Nil)
        case _ => Nil
      }
      case _ => Nil
    }
  }

  private def insertDummyVariable(s: String, index: Int): String = {
    val (start, end) = s.splitAt(index)
    start + "x" + end
  }

  private def findNodeInPosition(node: SpelNode, position: Int): Option[SpelNode] = {
    (node :: (0 until node.getChildCount).flatMap(i => findNodeInPosition(node.getChild(i), position)).toList)
      .filter(node => node.getStartPosition <= position && position <= node.getEndPosition)
      .sortBy(node => node.getEndPosition - node.getStartPosition).headOption
  }

  private def findPrevNodeInPosition(node: SpelNode, position: Int): Option[SpelNode] = {
    (node :: (0 until node.getChildCount).flatMap(i => findPrevNodeInPosition(node.getChild(i), position)).toList)
      .filter(node => node.getEndPosition <= position)
      .sortWith((a, b) => if (a.getEndPosition == b.getEndPosition) a.getStartPosition > b.getStartPosition else a.getEndPosition > b.getEndPosition).headOption
  }

  private def normalizedCaretPosition(inputValue: String, caretPosition2d: CaretPosition2d): Int = {
    inputValue.split("\n").take(caretPosition2d.row).map(_.length).sum + caretPosition2d.row + caretPosition2d.column
  }
}

@JsonCodec(decodeOnly = true)
case class CaretPosition2d(row: Int, column: Int)

// TODO: fromClass is used to calculate suggestion score - to show fields first, then class methods. Maybe we should
//  return score from BE?
@JsonCodec(encodeOnly = true)
case class ExpressionSuggestion(methodName: String, refClazz: TypingResult, fromClass: Boolean)

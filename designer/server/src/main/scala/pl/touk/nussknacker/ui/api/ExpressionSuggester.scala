package pl.touk.nussknacker.ui.api

import io.circe.generic.JsonCodec
import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast.{PropertyOrFieldReference, VariableReference}
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypingResult}
import pl.touk.nussknacker.engine.definition.TypeInfos
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition

import scala.collection.Map

class ExpressionSuggester {

  private lazy val parser = new org.springframework.expression.spel.standard.SpelExpressionParser

  def expressionSuggestions(inputValue: String, caretPosition2d: CaretPosition2d, variables: Map[String, RefClazz], typeDefinitions: TypeDefinitionSet): List[ExpressionSuggestion] = {
    val normalizedCaret = normalizedCaretPosition(inputValue, caretPosition2d)
    if (normalizedCaret == 0) {
      return Nil
    }
    val previousChar = inputValue.substring(normalizedCaret - 1, normalizedCaret)
    val shouldInsertDummyVariable = (previousChar == "#" || previousChar == ".") && (normalizedCaret == inputValue.length || !inputValue.charAt(normalizedCaret).isLetter)
    val input = if (shouldInsertDummyVariable) {
      insertDummyVariable(inputValue, normalizedCaret)
    } else {
      inputValue
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

            def extractClass(result: TypingResult): Option[String] = {
              result match {
                case r: TypedClass => Some(r.klass.getName)
                case _ => None
              }
            }

            methods.values.flatten
              .map(m => ExpressionSuggestion(m.name, RefClazz(extractClass(m.signatures.head.result), display = Some(m.signatures.head.result.display)), false))
              .toList
          }

          val prevNodeVariable = prevNode.map(n => n.toStringAST.stripPrefix("#"))
            .flatMap(v => variables.get(v))
          prevNodeVariable.flatMap(c => c.refClazzName)
            .flatMap(c => typeDefinitions.all.find(t => t.getClazz.getName == c)) // TODO: use get(Class[_])
            .map(filterClassMethods).getOrElse(Nil) ++
            prevNodeVariable.flatMap(_.fields).map(filterMapByName(_, p.getName).toList.map { case (methodName, clazzRef) => ExpressionSuggestion(methodName, clazzRef, fromClass = false) }).getOrElse(Nil)
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

@JsonCodec(decodeOnly = true)
case class ExpressionSuggestionRequest(expression: String, caretPosition2d: CaretPosition2d, variables: Map[String, RefClazz])

@JsonCodec(encodeOnly = true) //TODO: maybe replace refClazz with just `display` field? Looks like FE doesn't need more than that
case class ExpressionSuggestion(methodName: String, refClazz: RefClazz, fromClass: Boolean)

@JsonCodec
case class RefClazz(refClazzName: Option[String], display: Option[String] = None, union: Option[List[RefClazz]] = None, fields: Option[Map[String, RefClazz]] = None, params: Option[List[RefClazz]] = None)

package pl.touk.nussknacker.engine.spel

import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast.{PropertyOrFieldReference, VariableReference}
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, SupertypeClassResolutionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypedObjectTypingResult, TypedUnion, TypingResult}
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.dict.{KeysDictTyper, SimpleDictRegistry}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId

class SpelExpressionSuggesterWithTyper {
  private val strictTypeChecking = false
  private val strictMethodsChecking = false
  private val staticMethodInvocationsChecking = false
  private val methodExecutionForUnknownAllowed = false
  private val dynamicPropertyAccessAllowed = false
  private val classResolutionStrategy = SupertypeClassResolutionStrategy.Union
  private val commonSupertypeFinder = new CommonSupertypeFinder(classResolutionStrategy, strictTypeChecking)
  private val dict = new SimpleDictRegistry(Map.empty)
  private val parser = new org.springframework.expression.spel.standard.SpelExpressionParser()

  def expressionSuggestions(expression: Expression, normalizedCaretPosition: Int, variables: Map[String, TypingResult], typeDefinitions: TypeDefinitionSet): List[ExpressionSuggestion] = {
    val typer = new Typer(commonSupertypeFinder, new KeysDictTyper(dict), strictMethodsChecking, staticMethodInvocationsChecking,
      typeDefinitions, evaluationContextPreparer = null, methodExecutionForUnknownAllowed, dynamicPropertyAccessAllowed)
    val spelExpression = expression.expression
    if (normalizedCaretPosition == 0) {
      return Nil
    }
    val previousChar = spelExpression.substring(normalizedCaretPosition - 1, normalizedCaretPosition)
    val shouldInsertDummyVariable = (previousChar == "#" || previousChar == ".") && (normalizedCaretPosition == spelExpression.length || !spelExpression.charAt(normalizedCaretPosition).isLetter)
    val input = if (shouldInsertDummyVariable) {
      insertDummyVariable(spelExpression, normalizedCaretPosition)
    } else {
      spelExpression
    }

    def filterMapByName[V](map: Map[String, V], name: String): Map[String, V] = {
      if (shouldInsertDummyVariable) {
        map
      } else {
        map.filter { case (key, _) => key.toLowerCase.contains(name.toLowerCase) }
      }
    }

    val rawSpelExpression = parser.parseRaw(input)
    val ast = rawSpelExpression.getAST
    val nodeInPosition = findNodeInPosition(ast, normalizedCaretPosition)

    val suggestions = nodeInPosition match {
      case Some(node) => node match {
        case v: VariableReference =>
          val filteredVariables = filterMapByName(variables, v.toStringAST.stripPrefix("#"))
          filteredVariables.toList.map { case (variable, clazzRef) => ExpressionSuggestion(s"#$variable", clazzRef, fromClass = false) }
        case p: PropertyOrFieldReference =>
          val prevNodeInPosition = findPrevNodeInPosition(ast, p.getStartPosition)
          val typedPrevNode = prevNodeInPosition match {
            case Some(prevNode) => val spelExpressionWithoutNodeInPosition = input.substring(0, prevNode.getEndPosition) + input.substring(p.getEndPosition)
              val parsedSpelExpressionWithoutNodeInPosition = parser.parseRaw(spelExpressionWithoutNodeInPosition)
              val collectedTypingResult = typer.doTypeExpression(parsedSpelExpressionWithoutNodeInPosition, ValidationContext(variables))._2
              collectedTypingResult.intermediateResults.get(SpelNodeId(prevNode))
            case _ => None
          }

          def filterClassMethods(classDefinition: ClazzDefinition): List[ExpressionSuggestion] = {
            val methods = filterMapByName(classDefinition.methods, p.getName)

            methods.values.flatten
              .map(m => ExpressionSuggestion(m.name, m.signatures.head.result, fromClass = false))
              .toList
          }

          typedPrevNode.map(_.typingResult.withoutValue).map {
            case tc: TypedClass => typeDefinitions.get(tc.klass).map(filterClassMethods).getOrElse(Nil)
            case to: TypedObjectTypingResult => filterMapByName(to.fields, p.getName).toList.map { case (methodName, clazzRef) => ExpressionSuggestion(methodName, clazzRef, fromClass = false) }
            case tu: TypedUnion => tu.possibleTypes.map(_.objType.klass).flatMap(klass => typeDefinitions.get(klass).map(filterClassMethods).getOrElse(Nil)).toList
            case _ => Nil
          }.getOrElse(Nil)
        case _ => Nil
      }
      case _ => Nil
    }
    suggestions.sortBy(_.methodName)
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

}

package pl.touk.nussknacker.engine.spel

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression, TemplateParserContext}
import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast.{Identifier, Indexer, Projection, PropertyOrFieldReference, QualifiedIdentifier, Selection, StringLiteral, TypeReference, VariableReference}
import org.springframework.expression.spel.standard.SpelExpression
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.dict.UiDictServices
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, TypedClass, TypedDict, TypedObjectTypingResult, TypedObjectWithValue, TypedUnion, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.dict.LabelsDictTyper
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Typer.TypingResultWithContext
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class SpelExpressionSuggester(expressionConfig: ExpressionDefinition[_], typeDefinitions: TypeDefinitionSet, uiDictServices: UiDictServices, classLoader: ClassLoader) {
  private val successfulNil = Future.successful[List[ExpressionSuggestion]](Nil)
  private val typer = Typer.default(classLoader, expressionConfig, new LabelsDictTyper(uiDictServices.dictRegistry), typeDefinitions)
  private val nuSpelNodeParser = new NuSpelNodeParser(typer)
  private val dictQueryService = uiDictServices.dictQueryService

  def expressionSuggestions(expression: Expression, normalizedCaretPosition: Int, variables: Map[String, TypingResult])(implicit ec: ExecutionContext): Future[List[ExpressionSuggestion]] = {
    val spelExpression = expression.expression
    if (normalizedCaretPosition == 0) {
      return successfulNil
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

    def suggestionsForPropertyOrFieldReference(nodeInPosition: NuSpelNode, p: PropertyOrFieldReference): Future[Iterable[ExpressionSuggestion]] = {
      val typedPrevNode = nodeInPosition.prevNode().flatMap(_.typingResultWithContext)
      typedPrevNode.collect {
        case TypingResultWithContext(tc: TypedClass, staticContext) => Future.successful(typeDefinitions.get(tc.klass).map(c => filterClassMethods(c, p.getName, staticContext)).getOrElse(Nil))
        case TypingResultWithContext(to: TypedObjectWithValue, staticContext) => Future.successful(typeDefinitions.get(to.underlying.klass).map(c => filterClassMethods(c, p.getName, staticContext)).getOrElse(Nil))
        case TypingResultWithContext(to: TypedObjectTypingResult, _) =>
          val suggestionsFromFields = filterMapByName(to.fields, p.getName).toList.map { case (methodName, clazzRef) => ExpressionSuggestion(methodName, clazzRef, fromClass = false, None, Nil) }
          val suggestionsFromClass = typeDefinitions.get(to.objType.klass).map(c => filterClassMethods(c, p.getName, staticContext = false, fromClass = suggestionsFromFields.nonEmpty)).getOrElse(Nil)
          Future.successful(suggestionsFromFields ++ suggestionsFromClass)
        case TypingResultWithContext(tu: TypedUnion, staticContext) => Future.successful(tu.possibleTypes.map(_.objType.klass).flatMap(klass => typeDefinitions.get(klass).map(c => filterClassMethods(c, p.getName, staticContext)).getOrElse(Nil)))
        case TypingResultWithContext(td: TypedDict, _) => dictQueryService.queryEntriesByLabel(td.dictId, if (shouldInsertDummyVariable) "" else p.getName)
          .map(_.map(list => list.map(e => ExpressionSuggestion(e.label, td, fromClass = false, None, Nil)))).getOrElse(successfulNil)
      }.getOrElse(successfulNil)
    }

    def filterClassMethods(classDefinition: ClazzDefinition, name: String, staticContext: Boolean, fromClass: Boolean = false): List[ExpressionSuggestion] = {
      val methods = filterMapByName(if (staticContext) classDefinition.staticMethods else classDefinition.methods, name)

      methods.values.flatten
        .map { method =>
          // TODO: present all overloaded methods, not only one with most parameters.
          //  Current logic here is the same as in UIProcessObjectsFactory
          val signature = method.signatures.toList.maxBy(_.parametersToList.length)
          ExpressionSuggestion(method.name, signature.result, fromClass = fromClass, method.description, (signature.noVarArgs ::: signature.varArg.toList).map(p => Parameter(p.name, p.refClazz)))
        }
        .toList
    }

    val suggestions = for {
      (parsedSpelNode, adjustedPosition) <- nuSpelNodeParser.parse(input, expression.language, normalizedCaretPosition, variables).toOption.flatten
      nodeInPosition <- parsedSpelNode.findNodeInPosition(adjustedPosition)
    } yield {
      nodeInPosition.spelNode match {
        // variable is typed (#foo), so we need to return filtered list of all variables that match currently typed name
        case v: VariableReference =>
          // if the caret is inside projection or selection (eg #list.?[#<HERE>]) we add `this` to list of variables
          val thisTypingResult = for {
            parent <- nodeInPosition.parent.map(_.node)
            prevNode <- parent.prevNode().flatMap(_.typingResultWithContext)
          } yield {
            parent.spelNode match {
              case _: Selection | _: Projection => Some(determineIterableElementTypingResult(prevNode.typingResult))
              case _ => None
            }
          }
          val filteredVariables = filterMapByName(thisTypingResult.flatten.map("this" -> _).toMap ++ variables, v.toStringAST.stripPrefix("#"))
          Future.successful(filteredVariables.map { case (variable, clazzRef) => ExpressionSuggestion(s"#$variable", clazzRef, fromClass = false, None, Nil) })
        // property is typed (#foo.bar), so we need to return filtered list of all methods and fields from previous spel node type
        case p: PropertyOrFieldReference =>
          suggestionsForPropertyOrFieldReference(nodeInPosition, p)
        // suggestions for dictionary with indexer notation - #dict['Foo']
        // 1. caret is inside string
        // 2. parent node is Indexer - []
        // 3. parent's prev node is dictionary
        case s: StringLiteral =>
          val y = for {
            parent <- nodeInPosition.parent.map(_.node)
            parentPrevNode <- parent.prevNode()
            parentPrevNodeTyping <- parentPrevNode.typingResultWithContext.map(_.typingResult)
          } yield {
            parent.spelNode match {
              case _: Indexer => parentPrevNodeTyping match {
                case td: TypedDict => dictQueryService.queryEntriesByLabel(td.dictId, s.getLiteralValue.getValue.toString)
                  .map(_.map(list => list.map(e => ExpressionSuggestion(e.label, td, fromClass = false, None, Nil)))).getOrElse(successfulNil)
                case _ => successfulNil
              }
              case _ => successfulNil
            }
          }
          y.getOrElse(successfulNil)
        // suggestions for full class name inside TypeReference, eg T(java.time.Duration)
        case _: Identifier =>
          val r = for {
            parentNode <- nodeInPosition.parent
            grandparentNode <- parentNode.node.parent
          } yield {
            (parentNode.node.spelNode, grandparentNode.node.spelNode) match {
              case (q: QualifiedIdentifier, _: TypeReference) =>
                val name = if (shouldInsertDummyVariable) {
                  q.toStringAST.stripSuffix("x")
                } else {
                  q.toStringAST
                }
                typeDefinitions.typeDefinitions.keys.filter {
                  klass => klass.getName.startsWith(name)
                }.flatMap {
                  klass => klass.getName.stripPrefix(q.toStringAST.split('.').dropRight(1).mkString(".")).stripPrefix(".").split('.').headOption
                }.toSet.map {
                  ExpressionSuggestion(_, Unknown, fromClass = false, None, Nil)
                }
              case _ => Nil
            }
          }
          Future.successful(r.getOrElse(Nil))
        case _ => successfulNil
      }
    }
    suggestions.getOrElse(successfulNil).map(_.toList.sortBy(_.methodName))

  }

  private def insertDummyVariable(s: String, index: Int): String = {
    val (start, end) = s.splitAt(index)
    start + "x" + end
  }

  private def determineIterableElementTypingResult(parent: TypingResult): TypingResult = {
    parent match {
      case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[java.util.Collection[_]]) =>
        tc.objType.params.headOption.getOrElse(Unknown)
      case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[java.util.Map[_, _]]) =>
        TypedObjectTypingResult(List(
          ("key", tc.objType.params.headOption.getOrElse(Unknown)),
          ("value", tc.objType.params.drop(1).headOption.getOrElse(Unknown))))
      case tc: SingleTypingResult if tc.objType.klass.isArray =>
        tc.objType.params.headOption.getOrElse(Unknown)
      case _ => Unknown
    }
  }
}

private class NuSpelNodeParser(typer: Typer) extends LazyLogging {
  private val parser = new org.springframework.expression.spel.standard.SpelExpressionParser()

  def parse(input: String, language: String, position: Int, variables: Map[String, TypingResult]): Try[Option[(NuSpelNode, Int)]] = {
    val rawExpression = if (language == Expression.Language.Spel) {
      Try(parser.parseExpression(input))
    } else if (language == Expression.Language.SpelTemplate) {
      Try(parser.parseExpression(input, new TemplateParserContext()))
    } else {
      Failure(new IllegalArgumentException(s"Language $language is not supported"))
    }
    rawExpression.map { parsedExpression =>
      val collectedTypingResult = typer.doTypeExpression(parsedExpression, ValidationContext(variables))._2
      val parsedSpelExpression = parsedExpression match {
        case s: SpelExpression => Some((s, position))
        case c: CompositeStringExpression => findExpressionInPositionAndAdjustPosition(c, position)
        case _: LiteralExpression => None
      }
      parsedSpelExpression.map(e => (new NuSpelNode(e._1.getAST, None, collectedTypingResult), e._2))
    }.recoverWith {
      case e =>
        logger.debug(s"Failed to parse $language expression: $input, error: ${e.getMessage}")
        Failure(e)
    }
  }

  private def findExpressionInPositionAndAdjustPosition(expression: CompositeStringExpression, position: Int): Option[(SpelExpression, Int)] = {
    var currentPosition = 0
    for (e <- expression.getExpressions) {
      currentPosition += e.getExpressionString.length
      e match {
        case s: SpelExpression =>
          // FIXME: this method does not work if there are any spaces around spel expression, e.g. 'Hello #{ #world }
          //  SpelExpressionParser simply ignores them and pure expression is returned
          currentPosition += 3 // '#{'.length + '}'.length
          if (currentPosition > position) {
            return Some((s, position - (currentPosition - 1 - e.getExpressionString.length)))
          }
        case _ =>
          if (currentPosition > position) {
            return None
          }
      }

    }
    None
  }
}

private class NuSpelNode(val spelNode: SpelNode, val parent: Option[NuSpelNodeParent], collectedTypingResult: CollectedTypingResult) {
  val children: List[NuSpelNode] = (0 until spelNode.getChildCount).map { i =>
    new NuSpelNode(spelNode.getChild(i), Some(NuSpelNodeParent(this, i)), collectedTypingResult)
  }.toList
  val typingResultWithContext: Option[Typer.TypingResultWithContext] = collectedTypingResult.intermediateResults.get(SpelNodeId(spelNode))

  def findNodeInPosition(position: Int): Option[NuSpelNode] = {
    val allInPosition = (this :: children.flatMap(c => c.findNodeInPosition(position)))
      .filter(e => e.isInPosition(position))
    for {
      // scala 2.12 is missing minOption and findLast
      shortest <- minOption(allInPosition.map(e => e.positionLength))
      last <- allInPosition.reverse.find(e => e.positionLength == shortest)
    } yield last
  }

  def prevNode(): Option[NuSpelNode] = {
    parent.filter(_.nodeIndex > 0).map(p => p.node.children(p.nodeIndex - 1))
  }

  private def minOption(seq: Seq[Int]): Option[Int] = if (seq.isEmpty) {
    None
  } else {
    Some(seq.min)
  }

  private def isInPosition(position: Int): Boolean = spelNode.getStartPosition <= position && position <= spelNode.getEndPosition

  private def positionLength: Int = spelNode.getEndPosition - spelNode.getStartPosition

}

private case class NuSpelNodeParent(node: NuSpelNode, nodeIndex: Int)

// TODO: fromClass is used to calculate suggestion score - to show fields first, then class methods. Maybe we should
//  return score from BE?
@JsonCodec(encodeOnly = true)
case class ExpressionSuggestion(methodName: String, refClazz: TypingResult, fromClass: Boolean, description: Option[String], parameters: List[Parameter])

@JsonCodec(encodeOnly = true)
case class Parameter(name: String, refClazz: TypingResult)

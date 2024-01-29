package pl.touk.nussknacker.engine.spel

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.ast._
import org.springframework.expression.spel.standard.{SpelExpression => SpringSpelExpression}
import org.springframework.expression.spel.{SpelNode, SpelParserConfiguration}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.dict.UiDictServices
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.validation.Validations.isVariableNameValid
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.dict.LabelsDictTyper
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Typer.TypingResultWithContext
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId
import pl.touk.nussknacker.engine.spel.parser.NuTemplateAwareExpressionParser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class SpelExpressionSuggester(
    expressionConfig: ExpressionConfigDefinition,
    clssDefinitions: ClassDefinitionSet,
    uiDictServices: UiDictServices,
    classLoader: ClassLoader
) {
  private val successfulNil = Future.successful[List[ExpressionSuggestion]](Nil)
  private val typer =
    Typer.default(classLoader, expressionConfig, new LabelsDictTyper(uiDictServices.dictRegistry), clssDefinitions)
  private val nuSpelNodeParser = new NuSpelNodeParser(typer)
  private val dictQueryService = uiDictServices.dictQueryService

  def expressionSuggestions(expression: Expression, normalizedCaretPosition: Int, validationContext: ValidationContext)(
      implicit ec: ExecutionContext
  ): Future[List[ExpressionSuggestion]] = {
    val spelExpression = expression.expression
    if (normalizedCaretPosition == 0) {
      return successfulNil
    }
    val previousChar = spelExpression.substring(normalizedCaretPosition - 1, normalizedCaretPosition)
    val shouldInsertDummyVariable =
      (previousChar == "#" || previousChar == ".") && (normalizedCaretPosition == spelExpression.length || !spelExpression
        .charAt(normalizedCaretPosition)
        .isLetter)
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

    def suggestionsForPropertyOrFieldReference(
        nodeInPosition: NuSpelNode,
        p: PropertyOrFieldReference
    ): Future[Iterable[ExpressionSuggestion]] = {

      val nuSpelNodeParentOpt = nodeInPosition.parent.map(_.node)
      val parentIsIndexer     = nuSpelNodeParentOpt.exists(_.spelNode.isInstanceOf[Indexer])

      val typedNode = nuSpelNodeParentOpt.flatMap {
        case nuSpelNodeParent if parentIsIndexer =>
          nuSpelNodeParent.prevNode().flatMap(_.typingResultWithContext)
        case _ =>
          nodeInPosition.prevNode().flatMap(_.typingResultWithContext)
      }

      typedNode
        .collect {
          case TypingResultWithContext(tc: TypedClass, staticContext) =>
            Future.successful(
              clssDefinitions.get(tc.klass).map(c => filterClassMethods(c, p.getName, staticContext)).getOrElse(Nil)
            )
          case TypingResultWithContext(to: TypedObjectWithValue, staticContext) =>
            Future.successful(
              clssDefinitions
                .get(to.underlying.klass)
                .map(c => filterClassMethods(c, p.getName, staticContext))
                .getOrElse(Nil)
            )
          case TypingResultWithContext(to: TypedObjectTypingResult, _) =>
            def filterIllegalIdentifierAfterDot(name: String) = {
              isVariableNameValid(name)
            }
            val collectSuggestionsFromClass = parentIsIndexer
            val suggestionsFromFields = filterMapByName(to.fields, p.getName).toList
              .filter { case (fieldName, _) =>
                // TODO: signal to user that some values have been filtered
                filterIllegalIdentifierAfterDot(fieldName)
              }
              .map { case (methodName, clazzRef) =>
                ExpressionSuggestion(methodName, clazzRef, fromClass = false, None, Nil)
              }
            val suggestionsFromClass = clssDefinitions
              .get(to.objType.klass)
              .map(c => filterClassMethods(c, p.getName, staticContext = false, fromClass = true))
              .getOrElse(Nil)
            val applicableSuggestions = if (collectSuggestionsFromClass) {
              suggestionsFromFields
            } else {
              suggestionsFromFields ++ suggestionsFromClass
            }
            Future.successful(applicableSuggestions)
          case TypingResultWithContext(tu: TypedUnion, staticContext) =>
            Future.successful(
              tu.possibleTypes
                .map(_.objType.klass)
                .toList
                .flatMap(klass =>
                  clssDefinitions.get(klass).map(c => filterClassMethods(c, p.getName, staticContext)).getOrElse(Nil)
                )
                .distinct
            )
          case TypingResultWithContext(td: TypedDict, _) =>
            dictQueryService
              .queryEntriesByLabel(td.dictId, if (shouldInsertDummyVariable) "" else p.getName)
              .map(_.map(list => list.map(e => ExpressionSuggestion(e.label, td, fromClass = false, None, Nil))))
              .getOrElse(successfulNil)
        }
        .getOrElse(successfulNil)
    }

    def filterClassMethods(
        classDefinition: ClassDefinition,
        name: String,
        staticContext: Boolean,
        fromClass: Boolean = false
    ): List[ExpressionSuggestion] = {
      val methods = filterMapByName(if (staticContext) classDefinition.staticMethods else classDefinition.methods, name)

      methods.values.flatten.map { method =>
        // TODO: present all overloaded methods, not only one with most parameters.
        val signature = method.signatures.toList.maxBy(_.parametersToList.length)
        ExpressionSuggestion(
          method.name,
          signature.result,
          fromClass = fromClass,
          method.description,
          (signature.noVarArgs ::: signature.varArg.toList).map(p => Parameter(p.name, p.refClazz))
        )
      }.toList
    }

    val suggestions = for {
      (parsedSpelNode, adjustedPosition) <- nuSpelNodeParser
        .parse(input, expression.language, normalizedCaretPosition, validationContext)
        .toOption
        .flatten
      nodeInPosition <- parsedSpelNode.findNodeInPosition(adjustedPosition)
    } yield {
      nodeInPosition.spelNode match {
        // variable is typed (#foo), so we need to return filtered list of all variables that match currently typed name
        case v: VariableReference =>
          // if the caret is inside projection or selection (eg #list.?[#<HERE>]) we add `this` to list of variables
          val thisTypingResult = for {
            parent   <- nodeInPosition.parent.map(_.node)
            prevNode <- parent.prevNode().flatMap(_.typingResultWithContext)
          } yield {
            parent.spelNode match {
              case _: Selection | _: Projection => Some(determineIterableElementTypingResult(prevNode.typingResult))
              case _                            => None
            }
          }
          val filteredVariables = filterMapByName(
            thisTypingResult.flatten.map("this" -> _).toMap ++ validationContext.variables,
            v.toStringAST.stripPrefix("#")
          )
          Future.successful(filteredVariables.map { case (variable, clazzRef) =>
            ExpressionSuggestion(s"#$variable", clazzRef, fromClass = false, None, Nil)
          })
        // property is typed (#foo.bar), so we need to return filtered list of all methods and fields from previous spel node type
        case p: PropertyOrFieldReference =>
          suggestionsForPropertyOrFieldReference(nodeInPosition, p)
        // suggestions for dictionary with indexer notation - #dict['Foo']
        // 1. caret is inside string
        // 2. parent node is Indexer - []
        // 3. parent's prev node is dictionary
        case s: StringLiteral =>
          val y = for {
            parent               <- nodeInPosition.parent.map(_.node)
            parentPrevNode       <- parent.prevNode()
            parentPrevNodeTyping <- parentPrevNode.typingResultWithContext.map(_.typingResult)
          } yield {
            parent.spelNode match {
              case _: Indexer =>
                parentPrevNodeTyping match {
                  case td: TypedDict =>
                    dictQueryService
                      .queryEntriesByLabel(td.dictId, s.getLiteralValue.getValue.toString)
                      .map(
                        _.map(list =>
                          list.map(e => ExpressionSuggestion(e.label, td.valueType, fromClass = false, None, Nil))
                        )
                      )
                      .getOrElse(successfulNil)
                  case TypedObjectTypingResult(fields, _, _) =>
                    Future.successful(fields.map(f => ExpressionSuggestion(f._1, f._2, fromClass = false, None, Nil)))
                  case _ => successfulNil
                }
              case _ => successfulNil
            }
          }
          y.getOrElse(successfulNil)
        // suggestions for full class name inside TypeReference, eg T(java.time.Duration)
        case _: Identifier =>
          val r = for {
            parentNode      <- nodeInPosition.parent
            grandparentNode <- parentNode.node.parent
          } yield {
            (parentNode.node.spelNode, grandparentNode.node.spelNode) match {
              case (q: QualifiedIdentifier, _: TypeReference) =>
                val name = if (shouldInsertDummyVariable) {
                  q.toStringAST.stripSuffix("x")
                } else {
                  q.toStringAST
                }
                clssDefinitions.classDefinitionsMap.keys
                  .filter { klass =>
                    klass.getName.startsWith(name)
                  }
                  .flatMap { klass =>
                    klass.getName
                      .stripPrefix(q.toStringAST.split('.').dropRight(1).mkString("."))
                      .stripPrefix(".")
                      .split('.')
                      .headOption
                  }
                  .toSet
                  .map {
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
        Typed.record(
          Map(
            "key"   -> tc.objType.params.headOption.getOrElse(Unknown),
            "value" -> tc.objType.params.drop(1).headOption.getOrElse(Unknown)
          )
        )
      case tc: SingleTypingResult if tc.objType.klass.isArray =>
        tc.objType.params.headOption.getOrElse(Unknown)
      case _ => Unknown
    }
  }

}

private class NuSpelNodeParser(typer: Typer) extends LazyLogging {
  private val parser = new NuTemplateAwareExpressionParser(new SpelParserConfiguration)

  def parse(
      input: String,
      language: String,
      position: Int,
      validationContext: ValidationContext
  ): Try[Option[(NuSpelNode, Int)]] = {
    val rawExpression = language match {
      case Expression.Language.Spel         => Try(parser.parseExpression(input, null))
      case Expression.Language.SpelTemplate => Try(parser.parseExpression(input, new TemplateParserContext()))
      case _ => Failure(new IllegalArgumentException(s"Language $language is not supported"))
    }
    rawExpression
      .map { parsedExpressions =>
        parsedExpressions.find(e => e.start <= position && position <= e.end).flatMap { e =>
          e.expression match {
            case s: SpringSpelExpression =>
              val collectedTypingResult =
                typer.doTypeExpression(s, validationContext)._2
              Some((new NuSpelNode(s.getAST, collectedTypingResult), position - e.start))
            case _ => None
          }
        }
      }
      .recoverWith { case e =>
        logger.debug(s"Failed to parse $language expression: $input, error: ${e.getMessage}")
        Failure(e)
      }
  }

}

private class NuSpelNode(
    val spelNode: SpelNode,
    collectedTypingResult: CollectedTypingResult,
    val parent: Option[NuSpelNodeParent] = None
) {

  val children: List[NuSpelNode] = (0 until spelNode.getChildCount).map { i =>
    new NuSpelNode(spelNode.getChild(i), collectedTypingResult, Some(NuSpelNodeParent(this, i)))
  }.toList

  val typingResultWithContext: Option[Typer.TypingResultWithContext] =
    collectedTypingResult.intermediateResults.get(SpelNodeId(spelNode))

  def findNodeInPosition(position: Int): Option[NuSpelNode] = {
    val allInPosition = (this :: children.flatMap(c => c.findNodeInPosition(position)))
      .filter(e => e.isInPosition(position))
    for {
      // scala 2.12 is missing minOption and findLast
      shortest <- minOption(allInPosition.map(e => e.positionLength))
      last     <- allInPosition.reverse.find(e => e.positionLength == shortest)
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

  private def isInPosition(position: Int): Boolean =
    spelNode.getStartPosition <= position && position <= spelNode.getEndPosition

  private def positionLength: Int = spelNode.getEndPosition - spelNode.getStartPosition

}

private case class NuSpelNodeParent(node: NuSpelNode, nodeIndex: Int)

// TODO: fromClass is used to calculate suggestion score - to show fields first, then class methods. Maybe we should
//  return score from BE?
@JsonCodec(encodeOnly = true)
case class ExpressionSuggestion(
    methodName: String,
    refClazz: TypingResult,
    fromClass: Boolean,
    description: Option[String],
    parameters: List[Parameter]
)

@JsonCodec(encodeOnly = true)
case class Parameter(name: String, refClazz: TypingResult)

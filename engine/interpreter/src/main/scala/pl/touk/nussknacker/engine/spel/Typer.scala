package pl.touk.nussknacker.engine.spel

import cats.data.NonEmptyList._
import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import cats.instances.map._
import cats.kernel.{Monoid, Semigroup}
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression.Expression
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.ast._
import org.springframework.expression.spel.{SpelNode, standard}
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.{ExpressionParseError, ExpressionTypingInfo}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, NumberTypesPromotionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.dict.SpelDictTyper
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.spel.Typer._
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId
import pl.touk.nussknacker.engine.spel.ast.SpelNodePrettyPrinter
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer
import pl.touk.nussknacker.engine.spel.typer.{MapLikePropertyTyper, TypeMethodReference}
import pl.touk.nussknacker.engine.types.EspTypeUtils

import scala.annotation.tailrec
import scala.reflect.runtime._
import scala.util.{Failure, Success, Try}

private[spel] class Typer(classLoader: ClassLoader, commonSupertypeFinder: CommonSupertypeFinder,
                          dictTyper: SpelDictTyper, strictMethodsChecking: Boolean,
                          staticMethodInvocationsChecking: Boolean,
                          typeDefinitionSet: TypeDefinitionSet,
                          evaluationContextPreparer: EvaluationContextPreparer,
                          methodExecutionForUnknownAllowed: Boolean,
                          dynamicPropertyAccessAllowed: Boolean
                         )(implicit settings: ClassExtractionSettings) extends LazyLogging {

  import ast.SpelAst._

  type NodeTypingResult = ValidatedNel[ExpressionParseError, CollectedTypingResult]

  def typeExpression(expr: Expression, ctx: ValidationContext): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    expr match {
      case e: standard.SpelExpression =>
        typeExpression(e, ctx)
      case e: CompositeStringExpression =>
        val validatedParts = e.getExpressions.toList.map(typeExpression(_, ctx)).sequence
        // We drop intermediate results here:
        // * It's tricky to combine it as each of the subexpressions has it's own abstract tree with positions relative to the subexpression's starting position
        // * CompositeStringExpression is dedicated to template SpEL expressions. It cannot be nested (as templates cannot be nested)
        // * Currently we don't use intermediate typing results outside of Typer
        validatedParts.map(_ => CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Typed[String])))
      case e: LiteralExpression =>
        Valid(CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Typed[String])))
      case e: NullExpression =>
        Valid(CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Typed[String])))
    }
  }

  private def typeExpression(spelExpression: standard.SpelExpression, ctx: ValidationContext): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val ast = spelExpression.getAST
    val result = typeNode(ctx, ast, TypingContext(List.empty, Map.empty))
    logger.whenTraceEnabled {
      result match {
        case Valid(collectedResult) =>
          val printer = new SpelNodePrettyPrinter(n => collectedResult.intermediateResults.get(SpelNodeId(n)).map(_.display).getOrElse("NOT_TYPED"))
          logger.trace("typed valid expression: " + printer.print(ast))
        case Invalid(errors) =>
          logger.trace(s"typed invalid expression: ${spelExpression.getExpressionString}, errors: ${errors.toList.mkString(", ")}")
      }
    }
    result
  }

  private def typeNode(validationContext: ValidationContext, node: SpelNode, current: TypingContext): NodeTypingResult = {

    def toResult(typ: TypingResult) = current.toResult(TypedNode(node, TypingResultWithContext(typ)))

    def valid(typ: TypingResult) = Valid(toResult(typ))

    val withTypedChildren = typeChildren(validationContext, node, current) _

    def fixedWithNewCurrent(newCurrent: TypingContext) = typeChildrenAndReturnFixed(validationContext, node, newCurrent) _

    val fixed = fixedWithNewCurrent(current)

    def withChildrenOfType[Parts: universe.TypeTag](result: TypingResultWithContext) = withTypedChildren {
      case list if list.forall(_.typingResult.canBeSubclassOf(Typed.fromDetailedType[Parts])) => Valid(result)
      case _ => invalid("Wrong part types")
    }

    def catchUnexpectedErrors(block: => NodeTypingResult): NodeTypingResult = Try(block) match {
      case Success(value) =>
        value
      case Failure(e) =>
        throw new SpelCompilationException(node, e)
    }

    def typeUnion(e: Indexer, possibleTypes: Set[SingleTypingResult]): NodeTypingResult = {
      val typedPossibleTypes = possibleTypes.map(possibleType => typeIndexer(e, possibleType)).toList

      val typingResult = typedPossibleTypes.sequence.map(_.map(_.finalResult.typingResult).toSet).map(typingResults => Typed.apply(typingResults))
      typingResult.map(toResult)
    }

    @tailrec
    def typeIndexer(e: Indexer, typingResult: TypingResult): NodeTypingResult = {
      typingResult match {
        case TypedClass(clazz, param :: Nil) if clazz.isAssignableFrom(classOf[java.util.List[_]]) => valid(param)
        case TypedClass(clazz, keyParam :: valueParam :: Nil) if clazz.isAssignableFrom(classOf[java.util.Map[_, _]]) => valid(valueParam)
        case d: TypedDict => dictTyper.typeDictValue(d, e).map(toResult)
        case TypedUnion(possibleTypes) => typeUnion(e, possibleTypes)
        case TypedTaggedValue(underlying, _) => typeIndexer(e, underlying)
        case _ => if (dynamicPropertyAccessAllowed) valid(Unknown) else invalid("Dynamic property access is not allowed")
      }
    }

    catchUnexpectedErrors(node match {

      case e: Assign => invalid("Value modifications are not supported")
      case e: BeanReference => invalid("Bean reference is not supported")
      case e: CompoundExpression => e.children match {
        case first :: rest =>
          val validatedLastType = rest.foldLeft(typeNode(validationContext, first, current)) {
            case (Valid(prevResult), next) => typeNode(validationContext, next, current.pushOnStack(prevResult))
            case (invalid, _) => invalid
          }
          validatedLastType.map { lastType =>
            CollectedTypingResult(lastType.intermediateResults + (SpelNodeId(e) -> lastType.finalResult), lastType.finalResult)
          }
        //should not happen as CompoundExpression doesn't allow this...
        case Nil => valid(Unknown)
      }

      case e: ConstructorReference => withTypedChildren { _ =>
        val className = e.getChild(0).toStringAST
        val classToUse = Try(evaluationContextPreparer.prepareEvaluationContext(Context(""), Map.empty).getTypeLocator.findType(className)).toOption
        //TODO: validate constructor parameters...
        val clazz = classToUse.flatMap(kl => typeDefinitionSet.typeDefinitions.find(_.clazzName.klass == kl).map(_.clazzName))
        clazz match {
          case Some(typedClass) => Valid(TypingResultWithContext(typedClass))
          case None => invalid(s"Cannot create instance of unknown class $classToUse")
        }
      }

      case e: Elvis => withTypedChildren(l => Valid(TypingResultWithContext(Typed(l.map(_.typingResult).toSet))))
      //TODO: what should be here?
      case e: FunctionReference => valid(Unknown)

      //TODO: what should be here?
      case e: Identifier => valid(Unknown)
      //TODO: what should be here?
      case e: Indexer => current.stack.headOption match {
        case None => invalid("Cannot do indexing here")
        case Some(result) => typeIndexer(e, result.typingResult)
      }

      case e: BooleanLiteral => valid(Typed[Boolean])
      case e: IntLiteral => valid(Typed[java.lang.Integer])
      case e: LongLiteral => valid(Typed[java.lang.Long])
      case e: RealLiteral => valid(Typed(Typed[java.lang.Float]))
      case e: FloatLiteral => valid(Typed[java.lang.Float])
      case e: StringLiteral => valid(Typed[String])
      case e: NullLiteral => valid(Unknown)


      case e: InlineList => withTypedChildren { children =>
        //We don't want Typed.empty here, as currently it means it won't validate for any signature
        val elementType = if (children.isEmpty) TypingResultWithContext(Unknown) else TypingResultWithContext(Typed(children.map(typ => typ.typingResult).toSet))
        Valid(TypingResultWithContext(Typed.genericTypeClass[java.util.List[_]](List(elementType.typingResult))))
      }

      case e: InlineMap =>
        val zipped = e.children.zipWithIndex
        val keys = zipped.filter(_._2 % 2 == 0).map(_._1)
        val values = zipped.filter(_._2 % 2 == 1).map(_._1)
        val literalKeys = keys
          .collect {
            case a: PropertyOrFieldReference => a.getName
            case b: StringLiteral => b.getLiteralValue.getValue.toString
          }

        if (literalKeys.size != keys.size) {
          invalid("Currently inline maps with not literal keys (e.g. expressions as keys) are not supported")
        } else {
          values.map(typeNode(validationContext, _, current.withoutIntermediateResults)).sequence.andThen { typedValues =>
            withCombinedIntermediate(typedValues, current) { typedValues =>
              val typ = TypedObjectTypingResult(literalKeys.zip(typedValues.map(_.typingResult)))
              Valid(TypedNode(node, TypingResultWithContext(typ)))
            }
          }
        }

      case e: MethodReference =>
        extractMethodReference(e, validationContext, node, current, methodExecutionForUnknownAllowed)

      case e: OpEQ => checkEqualityLikeOperation(validationContext, e, current)
      case e: OpNE => checkEqualityLikeOperation(validationContext, e, current)

      case e: OpAnd => withChildrenOfType[Boolean](TypingResultWithContext(Typed[Boolean]))
      case e: OpOr => withChildrenOfType[Boolean](TypingResultWithContext(Typed[Boolean]))
      case e: OpGE => withChildrenOfType[Number](TypingResultWithContext(Typed[Boolean]))
      case e: OpGT => withChildrenOfType[Number](TypingResultWithContext(Typed[Boolean]))
      case e: OpLE => withChildrenOfType[Number](TypingResultWithContext(Typed[Boolean]))
      case e: OpLT => withChildrenOfType[Number](TypingResultWithContext(Typed[Boolean]))

      case e: OpDec => checkSingleOperandArithmeticOperation(validationContext, e, current)
      case e: OpInc => checkSingleOperandArithmeticOperation(validationContext, e, current)

      case e: OpDivide => checkTwoOperandsArithmeticOperation(validationContext, e, current)(NumberTypesPromotionStrategy.ForMathOperation)
      case e: OpMinus => withTypedChildren {
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) => Valid(TypingResultWithContext(commonSupertypeFinder.commonSupertype(left, right)(NumberTypesPromotionStrategy.ForMathOperation)))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil => invalid(s"Operator '${e.getOperatorName}' used with mismatch types: ${left.display} and ${right.display}")
        case TypingResultWithContext(left, _) :: Nil if left.canBeSubclassOf(Typed[Number]) => Valid(TypingResultWithContext(left))
        case TypingResultWithContext(left, _) :: Nil => invalid(s"Operator '${e.getOperatorName}' used with non numeric type: ${left.display}")
        case Nil => invalid("Empty minus")
      }
      case e: OpModulus => checkTwoOperandsArithmeticOperation(validationContext, e, current)(NumberTypesPromotionStrategy.ForMathOperation)
      case e: OpMultiply => checkTwoOperandsArithmeticOperation(validationContext, e, current)(NumberTypesPromotionStrategy.ForMathOperation)
      case e: OperatorPower => checkTwoOperandsArithmeticOperation(validationContext, e, current)(NumberTypesPromotionStrategy.ForPowerOperation)

      case e: OpPlus => withTypedChildren {
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left == Unknown || right == Unknown => Valid(TypingResultWithContext(Unknown))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[String]) || right.canBeSubclassOf(Typed[String]) => Valid(TypingResultWithContext(Typed[String]))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) => Valid(TypingResultWithContext(commonSupertypeFinder.commonSupertype(left, right)(NumberTypesPromotionStrategy.ForMathOperation)))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil => invalid(s"Operator '${e.getOperatorName}' used with mismatch types: ${left.display} and ${right.display}")
        case TypingResultWithContext(left, _) :: Nil if left.canBeSubclassOf(Typed[Number]) => Valid(TypingResultWithContext(left))
        case TypingResultWithContext(left, _) :: Nil => invalid(s"Operator '${e.getOperatorName}' used with non numeric type: ${left.display}")
        case Nil => invalid("Empty plus")
      }
      case e: OperatorBetween => fixed(TypingResultWithContext(Typed[Boolean]))
      case e: OperatorInstanceof => fixed(TypingResultWithContext(Typed[Boolean]))
      case e: OperatorMatches => withChildrenOfType[String](TypingResultWithContext(Typed[Boolean]))
      case e: OperatorNot => withChildrenOfType[Boolean](TypingResultWithContext(Typed[Boolean]))

      case e: Projection => current.stackHead match {
        case None => invalid("Cannot do projection here")
        //index, check if can project?
        case Some(iterateType) =>
          extractIterativeType(iterateType.typingResult).andThen { listType =>
            typeChildren(validationContext, node, current.pushOnStack(listType)) {
              case TypingResultWithContext(result, _) :: Nil => Valid(TypingResultWithContext(Typed.genericTypeClass[java.util.List[_]](List(result))))
              case other => invalid(s"Wrong selection type: ${other.map(_.display)}")
            }
          }
      }

      case e: PropertyOrFieldReference =>
        current.stackHead.map(head => extractProperty(e, head.typingResult).map(toResult)).getOrElse {
          invalid(s"Non reference '${e.toStringAST}' occurred. Maybe you missed '#' in front of it?")
        }
      //TODO: what should be here?
      case e: QualifiedIdentifier => fixed(TypingResultWithContext(Unknown))

      case e: Selection => current.stackHead match {
        case None => invalid("Cannot do selection here")
        case Some(iterateType) =>
          extractIterativeType(iterateType.typingResult).andThen { elementType =>
            typeChildren(validationContext, node, current.pushOnStack(elementType)) {
              case TypingResultWithContext(result, _) :: Nil if result.canBeSubclassOf(Typed[Boolean]) => Valid(resolveSelectionTypingResult(e, iterateType, elementType))
              case other => invalid(s"Wrong selection type: ${other.map(_.display)}")
            }
          }
      }

      case e: Ternary => withTypedChildren {
        case TypingResultWithContext(condition, _) :: TypingResultWithContext(onTrue, _) :: TypingResultWithContext(onFalse, _) :: Nil =>
          lazy val superType = commonSupertypeFinder.commonSupertype(onTrue, onFalse)(NumberTypesPromotionStrategy.ToSupertype)
          if (!condition.canBeSubclassOf(Typed[Boolean])) {
            invalid(s"Not a boolean expression used in ternary operator (expr ? onTrue : onFalse). Computed expression type: ${condition.display}")
          } else if (superType == Typed.empty) {
            invalid(s"Ternary operator (expr ? onTrue : onFalse) used with mismatch result types: ${onTrue.display} and ${onFalse.display}")
          } else {
            Valid(TypingResultWithContext(superType))
          }
        case _ => invalid("Invalid ternary operator") // shouldn't happen
      }

      case e: TypeReference =>
        if (staticMethodInvocationsChecking) {
          typeDefinitionSet.validateTypeReference(e, evaluationContextPreparer.prepareEvaluationContext(Context(""), Map.empty))
            .map(typedClass => current.toResult(TypedNode(e, TypingResultWithContext(typedClass, staticContext = true))))
        } else {
          valid(Unknown)
        }

      case e: VariableReference =>
        //only sane way of getting variable name :|
        val name = e.toStringAST.substring(1)
        validationContext.get(name).orElse(current.stackHead.map(_.typingResult).filter(_ => name == "this")) match {
          case Some(result) => valid(result)
          case None => invalid(s"Unresolved reference '$name'")
        }
    })
  }

  //currently there is no better way than to check ast string starting with $ or ^
  private def resolveSelectionTypingResult(node: Selection, parentType: TypingResultWithContext, childElementType: TypingResult) = {
    val isSingleElementSelection = List("$", "^").map(node.toStringAST.startsWith(_)).foldLeft(false)(_ || _)
    if (isSingleElementSelection) TypingResultWithContext(childElementType) else parentType
  }

  private def checkEqualityLikeOperation(validationContext: ValidationContext, node: Operator, current: TypingContext): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if commonSupertypeFinder.commonSupertype(right, left)(NumberTypesPromotionStrategy.ToSupertype) != Typed.empty => Valid(TypingResultWithContext(Typed[Boolean]))
      case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil => invalid(s"Operator '${node.getOperatorName}' used with not comparable types: ${left.display} and ${right.display}")
      case _ => invalid(s"Bad '${node.getOperatorName}' operator construction") // shouldn't happen
    }
  }

  private def checkTwoOperandsArithmeticOperation(validationContext: ValidationContext, node: Operator, current: TypingContext)
                                                 (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) => Valid(TypingResultWithContext(commonSupertypeFinder.commonSupertype(left, right)))
      case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil => invalid(s"Operator '${node.getOperatorName}' used with mismatch types: ${left.display} and ${right.display}")
      case _ => invalid(s"Bad '${node.getOperatorName}' operator construction") // shouldn't happen
    }
  }

  private def checkSingleOperandArithmeticOperation(validationContext: ValidationContext, node: Operator, current: TypingContext): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case TypingResultWithContext(left, _) :: Nil if left.canBeSubclassOf(Typed[Number]) => Valid(TypingResultWithContext(left))
      case TypingResultWithContext(left, _) :: Nil => invalid(s"Operator '${node.getOperatorName}' used with non numeric type: ${left.display}")
      case _ => invalid(s"Bad '${node.getOperatorName}' operator construction") // shouldn't happen
    }
  }

  private def extractProperty(e: PropertyOrFieldReference, t: TypingResult): ValidatedNel[ExpressionParseError, TypingResult] = t match {
    case Unknown =>
      if (methodExecutionForUnknownAllowed)
        Valid(Unknown)
      else
        invalid("Property access on Unknown is not allowed")
    case s: SingleTypingResult =>
      extractSingleProperty(e)(s)
    case TypedUnion(possible) =>
      val l = possible.toList.flatMap(single => extractSingleProperty(e)(single).toOption)
      if (l.isEmpty)
        invalid(s"There is no property '${e.getName}' in type: ${t.display}")
      else
        Valid(Typed(l.toSet))
  }

  private def extractMethodReference(reference: MethodReference, validationContext: ValidationContext, node: SpelNode, context: TypingContext, disableMethodExecutionForUnknown: Boolean) = {

    context.stack match {
      case head :: tail =>
        val isStatic = head.staticContext
        typeChildren(validationContext, node, context.copy(stack = tail)) { typedParams =>
          TypeMethodReference(reference.getName, head.typingResult, typedParams.map(_.typingResult), isStatic, methodExecutionForUnknownAllowed) match {
            case Right(typingResult) => Valid(TypingResultWithContext(typingResult))
            case Left(errorMsg) => if (strictMethodsChecking) invalid(errorMsg) else Valid(TypingResultWithContext(Unknown))
          }
        }
      case Nil =>
        invalid(s"Invalid method reference: ${reference.toStringAST}.")
    }
  }

  @tailrec
  private def extractSingleProperty(e: PropertyOrFieldReference)
                                   (t: SingleTypingResult): ValidatedNel[ExpressionParseError, TypingResult] = {
    t match {
      case tagged: TypedTaggedValue =>
        extractSingleProperty(e)(tagged.objType)
      case typedClass: TypedClass =>
        propertyTypeBasedOnMethod(e)(typedClass).orElse(MapLikePropertyTyper.mapLikeValueType(typedClass))
          .map(Valid(_))
          .getOrElse(invalid(s"There is no property '${e.getName}' in type: ${t.display}"))
      case TypedObjectTypingResult(fields, objType, _) =>
        val typeBasedOnFields = fields.get(e.getName)
        typeBasedOnFields.orElse(propertyTypeBasedOnMethod(e)(objType))
          .map(Valid(_))
          .getOrElse(invalid(s"There is no property '${e.getName}' in type: ${t.display}"))
      case dict: TypedDict =>
        dictTyper.typeDictValue(dict, e)
    }
  }

  private def propertyTypeBasedOnMethod(e: PropertyOrFieldReference)(typedClass: TypedClass) = {
    val clazzDefinition = EspTypeUtils.clazzDefinition(typedClass.klass)
    clazzDefinition.getPropertyOrFieldType(e.getName)
  }

  private def extractIterativeType(parent: TypingResult): Validated[NonEmptyList[ExpressionParseError], TypingResult] = parent match {
    case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[java.util.Collection[_]]) =>
      Valid(tc.objType.params.headOption.getOrElse(Unknown))
    case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[java.util.Map[_, _]]) =>
      Valid(TypedObjectTypingResult(List(
        ("key", tc.objType.params.headOption.getOrElse(Unknown)),
        ("value", tc.objType.params.drop(1).headOption.getOrElse(Unknown)))))
    case tc: SingleTypingResult if tc.objType.klass.isArray =>
      Valid(tc.objType.params.headOption.getOrElse(Unknown))
    case tc: SingleTypingResult => Validated.invalidNel(ExpressionParseError(s"Cannot do projection/selection on ${tc.display}"))
    //FIXME: what if more results are present?
    case _ => Valid(Unknown)
  }

  private def typeChildrenAndReturnFixed(validationContext: ValidationContext, node: SpelNode, current: TypingContext)(result: TypingResultWithContext)
  : Validated[NonEmptyList[ExpressionParseError], CollectedTypingResult] = {
    typeChildren(validationContext, node, current)(_ => Valid(result))
  }

  private def typeChildren(validationContext: ValidationContext, node: SpelNode, current: TypingContext)
                          (result: List[TypingResultWithContext] => ValidatedNel[ExpressionParseError, TypingResultWithContext])
  : ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val data = node.children.map(child => typeNode(validationContext, child, current.withoutIntermediateResults)).sequence
    data.andThen { collectedChildrenResults =>
      withCombinedIntermediate(collectedChildrenResults, current) { childrenResults =>
        result(childrenResults).map(TypedNode(node, _))
      }
    }
  }

  private def withCombinedIntermediate(intermediate: List[CollectedTypingResult], current: TypingContext)
                                      (result: List[TypingResultWithContext] => ValidatedNel[ExpressionParseError, TypedNode])
  : ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val intermediateResultsCombination = Monoid.combineAll(current.intermediateResults :: intermediate.map(_.intermediateResults))
    val intermediateTypes = intermediate.map(_.finalResult)
    result(intermediateTypes).map(CollectedTypingResult.withIntermediateAndFinal(intermediateResultsCombination, _))
  }

  private def invalid[T](message: String): ValidatedNel[ExpressionParseError, T] =
    Invalid(NonEmptyList.of(ExpressionParseError(message)))

  def withDictTyper(dictTyper: SpelDictTyper) =
    new Typer(classLoader, commonSupertypeFinder, dictTyper, strictMethodsChecking = strictMethodsChecking,
      staticMethodInvocationsChecking, typeDefinitionSet, evaluationContextPreparer, methodExecutionForUnknownAllowed, dynamicPropertyAccessAllowed)

}

object Typer {

  // This Semigroup is used in combining `intermediateResults: Map[SpelNodeId, TypingResult]` in TYper.
  // If there is no bug in Typer, collisions shouldn't happen
  implicit def notAcceptingMergingSemigroup: Semigroup[TypingResultWithContext] = new Semigroup[TypingResultWithContext] with LazyLogging {
    override def combine(x: TypingResultWithContext, y: TypingResultWithContext): TypingResultWithContext = {
      assert(x == y, s"Types not matching during combination of types for spel nodes: $x != $y")
      // merging the same types is not bad but it is a warning that sth went wrong e.g. typer typed something more than one time
      // or spel node's identity is broken
      logger.warn(s"Merging same types: $x for the same nodes. This shouldn't happen")
      x
    }
  }

  case class TypingResultWithContext(typingResult: TypingResult, staticContext: Boolean = false) {

    def display: String = typingResult.display
  }

  /**
    * It contains stack of types for recognition of nested node type.
    * intermediateResults are all results that we can collect for intermediate nodes
    */
  private case class TypingContext(stack: List[TypingResultWithContext], intermediateResults: Map[SpelNodeId, TypingResultWithContext]) {

    def pushOnStack(typingResultWithContext: TypingResultWithContext): TypingContext = copy(stack = typingResultWithContext :: stack)

    def pushOnStack(typingResult: TypingResult): TypingContext = copy(stack = TypingResultWithContext(typingResult) :: stack)

    def pushOnStack(typingResult: CollectedTypingResult): TypingContext =
      TypingContext(typingResult.finalResult :: stack, intermediateResults ++ typingResult.intermediateResults)

    def stackHead: Option[TypingResultWithContext] = stack.headOption

    def withoutIntermediateResults: TypingContext = copy(intermediateResults = Map.empty)

    def toResult(finalNode: TypedNode): CollectedTypingResult =
      CollectedTypingResult(intermediateResults + (finalNode.nodeId -> finalNode.typ), finalNode.typ)

  }

  class SpelCompilationException(node: SpelNode, cause: Throwable)
    extends RuntimeException(s"Can't compile SpEL expression: `${node.toStringAST}`, message: `${cause.getMessage}`.", cause)
}

private[spel] case class TypedNode(nodeId: SpelNodeId, typ: TypingResultWithContext)

private[spel] object TypedNode {

  def apply(node: SpelNode, typ: TypingResultWithContext): TypedNode =
    TypedNode(SpelNodeId(node), typ)

}

private[spel] case class CollectedTypingResult(intermediateResults: Map[SpelNodeId, TypingResultWithContext], finalResult: TypingResultWithContext) {
  def typingInfo: SpelExpressionTypingInfo = SpelExpressionTypingInfo(intermediateResults.map(intermediateResult => (intermediateResult._1 -> intermediateResult._2.typingResult)), finalResult.typingResult)
}

private[spel] object CollectedTypingResult {

  def withEmptyIntermediateResults(finalResult: TypingResultWithContext): CollectedTypingResult =
    CollectedTypingResult(Map.empty, finalResult)

  def withIntermediateAndFinal(intermediateResults: Map[SpelNodeId, TypingResultWithContext], finalNode: TypedNode): CollectedTypingResult = {
    CollectedTypingResult(intermediateResults + (finalNode.nodeId -> finalNode.typ), finalNode.typ)
  }

}

case class SpelExpressionTypingInfo(intermediateResults: Map[SpelNodeId, TypingResult],
                                    typingResult: TypingResult) extends ExpressionTypingInfo

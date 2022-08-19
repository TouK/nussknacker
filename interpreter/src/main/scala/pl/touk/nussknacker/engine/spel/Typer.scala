package pl.touk.nussknacker.engine.spel

import cats.data.NonEmptyList._
import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
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
import pl.touk.nussknacker.engine.api.expression._
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, NumberTypesPromotionStrategy, SupertypeClassResolutionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.dict.SpelDictTyper
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.{DynamicPropertyAccessError, IllegalIndexingOperation, IllegalProjectionSelectionError, IllegalPropertyAccessError, InvalidMethodReference}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.{ConstructionOfUnknown, NoPropertyError, NonReferenceError, UnresolvedReferenceError}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.OperatorError.{BadOperatorConstructionError, DivisionByZeroError, EmptyOperatorError, OperatorMismatchTypeError, OperatorNonNumericError, OperatorNotComparableError, TakingModuloZeroError}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.PartTypeError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.SelectionProjectionError.{IllegalProjectionError, IllegalSelectionError, IllegalSelectionTypeError}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.TernaryOperatorError.{InvalidTernaryOperator, TernaryOperatorMismatchTypesError, TernaryOperatorNotBooleanError}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.UnsupportedOperationError.{BeanReferenceError, MapWithExpressionKeysError, ModificationError}
import pl.touk.nussknacker.engine.spel.Typer._
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId
import pl.touk.nussknacker.engine.spel.ast.SpelNodePrettyPrinter
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer
import pl.touk.nussknacker.engine.spel.typer.{MapLikePropertyTyper, TypeMethodReference}
import pl.touk.nussknacker.engine.types.EspTypeUtils
import pl.touk.nussknacker.engine.util.MathUtils

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
      case _ => PartTypeError.invalidNel
    }

    def withTwoChildrenOfType[A: universe.TypeTag, R: universe.TypeTag](op: (A, A) => R) = {
      val expectedType = Typed.fromDetailedType[A]
      val resultType = Typed.fromDetailedType[R]
      withTypedChildren {
        case lst@left :: right :: Nil if lst.forall(_.typingResult.canBeSubclassOf(expectedType)) =>
          val res = left.typingResult.value
            .flatMap(l => right.typingResult.value.map((l, _)))
            .map{ case (x, y) => op(x.asInstanceOf[A], y.asInstanceOf[A]) }
          TypingResultWithContext(res.map(Typed.fromInstance).getOrElse(resultType)).validNel
        case _ => PartTypeError.invalidNel
      }
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
        case TypedClass(clazz, param :: Nil) if clazz.isAssignableFrom(classOf[java.util.List[_]]) || clazz.isAssignableFrom(classOf[Array[Object]]) => valid(param)
        case TypedClass(clazz, keyParam :: valueParam :: Nil) if clazz.isAssignableFrom(classOf[java.util.Map[_, _]]) => valid(valueParam)
        case d: TypedDict => dictTyper.typeDictValue(d, e).map(toResult)
        case TypedUnion(possibleTypes) => typeUnion(e, possibleTypes)
        case TypedTaggedValue(underlying, _) => typeIndexer(e, underlying)
        case _ => if (dynamicPropertyAccessAllowed) valid(Unknown) else DynamicPropertyAccessError.invalidNel
      }
    }

    catchUnexpectedErrors(node match {

      case e: Assign => ModificationError.invalidNel
      case e: BeanReference => BeanReferenceError.invalidNel
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
          case None => ConstructionOfUnknown(classToUse).invalidNel
        }
      }

      case e: Elvis => withTypedChildren(l => Valid(TypingResultWithContext(Typed(l.map(_.typingResult).toSet))))
      //TODO: what should be here?
      case e: FunctionReference => valid(Unknown)

      //TODO: what should be here?
      case e: Identifier => valid(Unknown)
      //TODO: what should be here?
      case e: Indexer => current.stack.headOption match {
        case None => IllegalIndexingOperation.invalidNel
        case Some(result) => typeIndexer(e, result.typingResult)
      }

      case e: Literal => valid(Typed.fromInstance(e.getLiteralValue.getValue))

      case e: InlineList => withTypedChildren { children =>
        val localSupertypeFinder = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.AnySuperclass, true)
        def getSupertype(a: TypingResult, b: TypingResult): TypingResult =
          localSupertypeFinder.commonSupertype(a, b)(NumberTypesPromotionStrategy.ToSupertype)

        //We don't want Typed.empty here, as currently it means it won't validate for any signature
        val elementType = if (children.isEmpty) TypingResultWithContext(Unknown)
          else TypingResultWithContext(children.map(typ => typ.typingResult).reduce(getSupertype))
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
          MapWithExpressionKeysError.invalidNel
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

      case e: OpEQ => checkEqualityLikeOperation(validationContext, e, current, isEquality = true)
      case e: OpNE => checkEqualityLikeOperation(validationContext, e, current, isEquality = false)

      case e: OpAnd => withTwoChildrenOfType[Boolean, Boolean](_ && _)
      case e: OpOr => withTwoChildrenOfType[Boolean, Boolean](_ || _)
      case e: OpGE => withTwoChildrenOfType[Number, Boolean](MathUtils.greaterOrEqual)
      case e: OpGT => withTwoChildrenOfType[Number, Boolean](MathUtils.greater)
      case e: OpLE => withTwoChildrenOfType[Number, Boolean](MathUtils.lesserOrEqual)
      case e: OpLT => withTwoChildrenOfType[Number, Boolean](MathUtils.lesser)

      case e: OpDec => checkSingleOperandArithmeticOperation(validationContext, e, current)(Some(MathUtils.minus(_, 1).validNel))
      case e: OpInc => checkSingleOperandArithmeticOperation(validationContext, e, current)(Some(MathUtils.plus(_, 1).validNel))

      case e: OpDivide =>
        val op = Some((x: Number, y: Number) =>
          if (y.doubleValue() == 0) DivisionByZeroError(x, y).invalidNel
          else MathUtils.divide(x, y).validNel)
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(op)(NumberTypesPromotionStrategy.ForMathOperation)

      case e: OpMinus => withTypedChildren {
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil
          if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) =>
          val supertype = commonSupertypeFinder.commonSupertype(left, right)(NumberTypesPromotionStrategy.ForMathOperation).withoutValue
          val result = operationOnTypesValue[Number, Number, Number](left, right)(MathUtils.minus(_, _).validNel).getOrElse(supertype.validNel)
          result.map(TypingResultWithContext(_))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil
          if left == right =>
          OperatorNonNumericError(e.getOperatorName, left).invalidNel
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil =>
          OperatorMismatchTypeError(e.getOperatorName, left, right).invalidNel
        case TypingResultWithContext(left, _) :: Nil if left.canBeSubclassOf(Typed[Number]) =>
          val resultType = left.withoutValue
          val result = operationOnTypesValue[Number, Number](left)(MathUtils.negate(_).validNel).getOrElse(resultType.validNel)
          result.map(TypingResultWithContext(_))
        case TypingResultWithContext(left, _) :: Nil =>
          OperatorNonNumericError(e.getOperatorName, left).invalidNel
        case Nil =>
          EmptyOperatorError(e.getOperatorName).invalidNel
      }
      case e: OpModulus =>
        val op = Some((x: Number, y: Number) =>
          if (y.doubleValue() == 0) TakingModuloZeroError(x, y).invalidNel
          else MathUtils.remainder(x, y).validNel)
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(op)(NumberTypesPromotionStrategy.ForMathOperation)
      case e: OpMultiply =>
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(Some(MathUtils.multiply(_, _).validNel))(NumberTypesPromotionStrategy.ForMathOperation)
      case e: OperatorPower =>
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(None)(NumberTypesPromotionStrategy.ForPowerOperation)

      case e: OpPlus => withTypedChildren {
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left == Unknown || right == Unknown =>
          Valid(TypingResultWithContext(Unknown))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[String]) || right.canBeSubclassOf(Typed[String]) =>
          val result = operationOnTypesValue[Any, Any, String](left, right)((l, r) => (l.toString + r.toString).validNel).getOrElse(Typed[String].validNel)
          result.map(TypingResultWithContext(_))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) =>
          val supertype = commonSupertypeFinder.commonSupertype(left, right)(NumberTypesPromotionStrategy.ForMathOperation).withoutValue
          val result = operationOnTypesValue[Number, Number, Number](left, right)(MathUtils.plus(_, _).validNel).getOrElse(supertype.validNel)
          result.map(TypingResultWithContext(_))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil =>
          OperatorMismatchTypeError(e.getOperatorName, left, right).invalidNel
        case TypingResultWithContext(left, _) :: Nil if left.canBeSubclassOf(Typed[Number]) =>
          Valid(TypingResultWithContext(left))
        case TypingResultWithContext(left, _) :: Nil =>
          OperatorNonNumericError(e.getOperatorName, left).invalidNel
        case Nil => EmptyOperatorError(e.getOperatorName).invalidNel
      }
      case e: OperatorBetween => fixed(TypingResultWithContext(Typed[Boolean]))
      case e: OperatorInstanceof => fixed(TypingResultWithContext(Typed[Boolean]))
      case e: OperatorMatches => withChildrenOfType[String](TypingResultWithContext(Typed[Boolean]))
      case e: OperatorNot => withChildrenOfType[Boolean](TypingResultWithContext(Typed[Boolean]))

      case e: Projection => current.stackHead match {
        case None => IllegalProjectionError.invalidNel
        //index, check if can project?
        case Some(iterateType) =>
          extractIterativeType(iterateType.typingResult).andThen { listType =>
            typeChildren(validationContext, node, current.pushOnStack(listType)) {
              case TypingResultWithContext(result, _) :: Nil => Valid(TypingResultWithContext(Typed.genericTypeClass[java.util.List[_]](List(result))))
              case other => IllegalSelectionTypeError(other.map(_.typingResult)).invalidNel
            }
          }
      }

      case e: PropertyOrFieldReference =>
        current.stackHead.map(head => extractProperty(e, head.typingResult).map(toResult)).getOrElse {
          NonReferenceError(e.toStringAST).invalidNel
        }
      //TODO: what should be here?
      case e: QualifiedIdentifier => fixed(TypingResultWithContext(Unknown))

      case e: Selection => current.stackHead match {
        case None => IllegalSelectionError.invalidNel
        case Some(iterateType) =>
          extractIterativeType(iterateType.typingResult).andThen { elementType =>
            typeChildren(validationContext, node, current.pushOnStack(elementType)) {
              case TypingResultWithContext(result, _) :: Nil if result.canBeSubclassOf(Typed[Boolean]) => Valid(resolveSelectionTypingResult(e, iterateType, elementType))
              case other => IllegalSelectionTypeError(other.map(_.typingResult)).invalidNel
            }
          }
      }

      case e: Ternary => withTypedChildren {
        case TypingResultWithContext(condition, _) :: TypingResultWithContext(onTrue, _) :: TypingResultWithContext(onFalse, _) :: Nil =>
          lazy val superType = commonSupertypeFinder.commonSupertype(onTrue, onFalse)(NumberTypesPromotionStrategy.ToSupertype)
          if (!condition.canBeSubclassOf(Typed[Boolean])) {
            TernaryOperatorNotBooleanError(condition).invalidNel
          } else if (superType == Typed.empty) {
            TernaryOperatorMismatchTypesError(onTrue, onFalse).invalidNel
          } else {
            Valid(TypingResultWithContext(superType))
          }
        case _ => InvalidTernaryOperator.invalidNel // shouldn't happen
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
          case None => UnresolvedReferenceError(name).invalidNel
        }
    })
  }

  private def operationOnTypesValue[A, R](typ: TypingResult)
                                         (op: A => ValidatedNel[ExpressionParseError, R]): Option[ValidatedNel[ExpressionParseError, TypingResult]] =
    typ.value.map(v => op(v.asInstanceOf[A]).map(Typed.fromInstance))

  private def operationOnTypesValue[A, B, R](left: TypingResult, right: TypingResult)
                                            (op: (A, B) => ValidatedNel[ExpressionParseError, R]): Option[ValidatedNel[ExpressionParseError, TypingResult]] =
    for {
      leftValue <- left.value
      rightValue <- right.value
      res = op(leftValue.asInstanceOf[A], rightValue.asInstanceOf[B])
    } yield res.map(Typed.fromInstance)

  //currently there is no better way than to check ast string starting with $ or ^
  private def resolveSelectionTypingResult(node: Selection, parentType: TypingResultWithContext, childElementType: TypingResult) = {
    val isSingleElementSelection = List("$", "^").map(node.toStringAST.startsWith(_)).foldLeft(false)(_ || _)
    if (isSingleElementSelection) TypingResultWithContext(childElementType) else parentType
  }

  private def checkEqualityLikeOperation(validationContext: ValidationContext,
                                         node: Operator,
                                         current: TypingContext,
                                         isEquality: Boolean): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case TypingResultWithContext(TypedObjectWithValue(leftVariable, leftValue), _) ::
        TypingResultWithContext(TypedObjectWithValue(rightVariable, rightValue), _) :: Nil =>
        checkEqualityComparableTypes(leftVariable, rightVariable, node)
          .map(x => TypedObjectWithValue(x.asInstanceOf[TypedClass], leftValue == rightValue ^ !isEquality))
          .map(TypingResultWithContext(_))
      case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil =>
        checkEqualityComparableTypes(left, right, node).map(TypingResultWithContext(_))
      case _ =>
        BadOperatorConstructionError(node.getOperatorName).invalidNel // shouldn't happen
    }
  }

  private def checkEqualityComparableTypes(left: TypingResult, right: TypingResult, node: Operator): ValidatedNel[ExpressionParseError, TypingResult] = {
    if (commonSupertypeFinder.commonSupertype(left, right)(NumberTypesPromotionStrategy.ToSupertype) != Typed.empty) {
      Valid(Typed[Boolean])
    } else
      OperatorNotComparableError(node.getOperatorName, left, right).invalidNel
  }

  private def checkTwoOperandsArithmeticOperation(validationContext: ValidationContext, node: Operator, current: TypingContext)
                                                 (op: Option[(Number, Number) => ValidatedNel[ExpressionParseError, Any]])
                                                 (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
      typeChildren(validationContext, node, current) {
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) =>
          val supertype = commonSupertypeFinder.commonSupertype(left, right).withoutValue
          val validatedType = op.flatMap(operationOnTypesValue[Number, Number, Any](left, right)(_)).getOrElse(supertype.validNel)
          validatedType.map(TypingResultWithContext(_))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil =>
          OperatorMismatchTypeError(node.getOperatorName, left, right).invalidNel
        case _ => BadOperatorConstructionError(node.getOperatorName).invalidNel // shouldn't happen
      }
    }

  private def checkSingleOperandArithmeticOperation(validationContext: ValidationContext, node: Operator, current: TypingContext)
                                                   (op: Option[Number => ValidatedNel[ExpressionParseError, Any]]):
    ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case TypingResultWithContext(left, _) :: Nil if left.canBeSubclassOf(Typed[Number]) =>
        val validatedResult = op.flatMap(operationOnTypesValue[Number, Any](left)(_)).getOrElse(left.withoutValue.validNel)
        validatedResult.map(TypingResultWithContext(_))
      case TypingResultWithContext(left, _) :: Nil =>
        OperatorNonNumericError(node.getOperatorName, left).invalidNel
      case _ => BadOperatorConstructionError(node.getOperatorName).invalidNel // shouldn't happen
    }
  }

  private def extractProperty(e: PropertyOrFieldReference, t: TypingResult): ValidatedNel[ExpressionParseError, TypingResult] = t match {
    case Unknown =>
      if (methodExecutionForUnknownAllowed)
        Valid(Unknown)
      else
        IllegalPropertyAccessError(Unknown).invalidNel
    case TypedNull =>
      IllegalPropertyAccessError(TypedNull).invalidNel
    case s: SingleTypingResult =>
      extractSingleProperty(e)(s)
    case TypedUnion(possible) =>
      val l = possible.toList.flatMap(single => extractSingleProperty(e)(single).toOption)
      if (l.isEmpty)
        NoPropertyError(t, e.getName).invalidNel
      else
        Valid(Typed(l.toSet))
  }

  private def extractMethodReference(reference: MethodReference,
                                     validationContext: ValidationContext,
                                     node: SpelNode,
                                     context: TypingContext,
                                     disableMethodExecutionForUnknown: Boolean): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {

    context.stack match {
      case head :: tail =>
        val isStatic = head.staticContext
        typeChildren(validationContext, node, context.copy(stack = tail)) { typedParams =>
          TypeMethodReference(
            reference.getName, 
            head.typingResult, 
            typedParams.map(_.typingResult),
            isStatic,
            methodExecutionForUnknownAllowed
          ).map(TypingResultWithContext(_)) match {
            case Right(x) => x.validNel
            case Left(x) if strictMethodsChecking => x.invalidNel
            case Left(_) => TypingResultWithContext(Unknown).validNel
          }
        }
      case Nil =>
        InvalidMethodReference(reference.toStringAST).invalidNel
    }
  }

  @tailrec
  private def extractSingleProperty(e: PropertyOrFieldReference)
                                   (t: SingleTypingResult): ValidatedNel[ExpressionParseError, TypingResult] = {
    t match {
      case typedObjectWithData: TypedObjectWithData =>
        extractSingleProperty(e)(typedObjectWithData.objType)
      case typedClass: TypedClass =>
        propertyTypeBasedOnMethod(e)(typedClass).orElse(MapLikePropertyTyper.mapLikeValueType(typedClass))
          .map(Valid(_))
          .getOrElse(NoPropertyError(t, e.getName).invalidNel)
      case TypedObjectTypingResult(fields, objType, _) =>
        val typeBasedOnFields = fields.get(e.getName)
        typeBasedOnFields.orElse(propertyTypeBasedOnMethod(e)(objType))
          .map(Valid(_))
          .getOrElse(NoPropertyError(t, e.getName).invalidNel)
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
    case tc: SingleTypingResult => Validated.invalidNel(IllegalProjectionSelectionError(tc))
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

  def withDictTyper(dictTyper: SpelDictTyper) =
    new Typer(classLoader, commonSupertypeFinder, dictTyper, strictMethodsChecking = strictMethodsChecking,
      staticMethodInvocationsChecking, typeDefinitionSet, evaluationContextPreparer, methodExecutionForUnknownAllowed, dynamicPropertyAccessAllowed)

}

object Typer {

  // This Semigroup is used in combining `intermediateResults: Map[SpelNodeId, TypingResult]` in Typer.
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

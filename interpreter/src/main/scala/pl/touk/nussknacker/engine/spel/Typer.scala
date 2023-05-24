package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, ValidatedNel, Writer}
import cats.implicits.catsSyntaxValidatedId
import cats.instances.list._
import cats.instances.map._
import cats.kernel.{Monoid, Semigroup}
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.ast._
import org.springframework.expression.spel.{SpelNode, standard}
import org.springframework.expression.{EvaluationContext, Expression}
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression._
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, NumberTypesPromotionStrategy, SupertypeClassResolutionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.dict.SpelDictTyper
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError._
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.{ConstructionOfUnknown, NoPropertyError, NonReferenceError, UnresolvedReferenceError}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.OperatorError._
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.PartTypeError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.SelectionProjectionError.{IllegalProjectionError, IllegalSelectionError, IllegalSelectionTypeError}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.TernaryOperatorError.{InvalidTernaryOperator, TernaryOperatorMismatchTypesError, TernaryOperatorNotBooleanError}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.UnsupportedOperationError.{BeanReferenceError, MapWithExpressionKeysError, ModificationError}
import pl.touk.nussknacker.engine.spel.Typer._
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId
import pl.touk.nussknacker.engine.spel.ast.SpelNodePrettyPrinter
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer
import pl.touk.nussknacker.engine.spel.typer.{MapLikePropertyTyper, MethodReferenceTyper, TypeReferenceTyper}
import pl.touk.nussknacker.engine.util.MathUtils

import scala.annotation.tailrec
import scala.reflect.runtime._
import scala.util.{Failure, Success, Try}

private[spel] class Typer(commonSupertypeFinder: CommonSupertypeFinder,
                          dictTyper: SpelDictTyper, strictMethodsChecking: Boolean,
                          staticMethodInvocationsChecking: Boolean,
                          typeDefinitionSet: TypeDefinitionSet,
                          evaluationContextPreparer: EvaluationContextPreparer,
                          methodExecutionForUnknownAllowed: Boolean,
                          dynamicPropertyAccessAllowed: Boolean) extends LazyLogging {

  import ast.SpelAst._

  private lazy val evaluationContext: EvaluationContext = evaluationContextPreparer.prepareEvaluationContext(Context(""), Map.empty)

  private val methodReferenceTyper = new MethodReferenceTyper(typeDefinitionSet, methodExecutionForUnknownAllowed)

  private lazy val typeReferenceTyper = new TypeReferenceTyper(evaluationContext, typeDefinitionSet)

  type TypingR[T] = Writer[List[ExpressionParseError], T]
  type NodeTypingResult = TypingR[CollectedTypingResult]

  def typeExpression(expr: Expression, ctx: ValidationContext): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val (errors, result) = doTypeExpression(expr, ctx)
    NonEmptyList.fromList(errors).map(_.invalid).getOrElse(result.valid)
  }

  def doTypeExpression(expr: Expression, ctx: ValidationContext): (List[ExpressionParseError], CollectedTypingResult) = {
    expr match {
      case e: standard.SpelExpression =>
        typeExpression(e, ctx)
      case e: CompositeStringExpression =>
        val (errors, _) = e.getExpressions.toList.map(doTypeExpression(_, ctx)).sequence
        // We drop intermediate results here:
        // * It's tricky to combine it as each of the subexpressions has it's own abstract tree with positions relative to the subexpression's starting position
        // * CompositeStringExpression is dedicated to template SpEL expressions. It cannot be nested (as templates cannot be nested)
        // * Currently we don't use intermediate typing results outside of Typer
        (errors, CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Typed[String])))
      case _: LiteralExpression =>
        (Nil, CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Typed[String])))
      case _: NullExpression =>
        (Nil, CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Typed[String])))
    }
  }

  private def typeExpression(spelExpression: standard.SpelExpression, ctx: ValidationContext): (List[ExpressionParseError], CollectedTypingResult) = {
    val ast = spelExpression.getAST
    val (errors, collectedResult) = typeNode(ctx, ast, TypingContext(List.empty, Map.empty)).run
    logger.whenTraceEnabled {
      val printer = new SpelNodePrettyPrinter(n => collectedResult.intermediateResults.get(SpelNodeId(n)).map(_.display).getOrElse("NOT_TYPED"))
      logger.trace(s"typed nodes: ${printer.print(ast)}, errors: ${errors.mkString(", ")}")
    }
    (errors, collectedResult)
  }

  private def typeNode(validationContext: ValidationContext, node: SpelNode, current: TypingContext): NodeTypingResult = {

    def toResult(typ: TypingResult) = current.toResult(TypedNode(node, TypingResultWithContext(typ)))

    def valid(typ: TypingResult) = Writer(List.empty[ExpressionParseError], toResult(typ))

    val withTypedChildren = typeChildren(validationContext, node, current) _

    def fixedWithNewCurrent(newCurrent: TypingContext) = typeChildrenAndReturnFixed(validationContext, node, newCurrent) _

    val fixed = fixedWithNewCurrent(current)

    def withChildrenOfType[Parts: universe.TypeTag](result: TypingResultWithContext) = {
      val w = Writer(List.empty[ExpressionParseError], result)
      withTypedChildren {
        case list if list.forall(_.typingResult.canBeSubclassOf(Typed.fromDetailedType[Parts])) => w
        case _ => w.tell(List(PartTypeError))
      }
    }

    def withTwoChildrenOfType[A: universe.TypeTag, R: universe.TypeTag](op: (A, A) => R) = {
      val castExpectedType = CastTypedValue[A]()
      val resultType = Typed.fromDetailedType[R]
      withTypedChildren { typingResultWithContextList =>
        typingResultWithContextList.map(_.typingResult) match {
          case castExpectedType(left) :: castExpectedType(right) :: Nil =>
            val typeFromOp = for {
              leftValue <- left.valueOpt
              rightValue <- right.valueOpt
              res = op(leftValue, rightValue)
            } yield Typed.fromInstance(res)
            Writer(List.empty, TypingResultWithContext(typeFromOp.getOrElse(resultType)))
          case _ => Writer(List(PartTypeError), TypingResultWithContext(resultType))
        }
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
        case _ =>
          val w = valid(Unknown)
          if (dynamicPropertyAccessAllowed) w else w.tell(List(DynamicPropertyAccessError))
      }
    }

    catchUnexpectedErrors(node match {

      case e: Assign => Writer(List(ModificationError), CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Unknown)))
      case e: BeanReference => Writer(List(BeanReferenceError), CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Unknown)))
      case e: CompoundExpression => e.children match {
        case first :: rest =>
          val validatedLastType = rest.foldLeft(typeNode(validationContext, first, current)) {
            case (prevW, next) =>
              prevW.flatMap { prevResult =>
                typeNode(validationContext, next, current.pushOnStack(prevResult))
              }
          }
          validatedLastType.map { lastType =>
            CollectedTypingResult(lastType.intermediateResults + (SpelNodeId(e) -> lastType.finalResult), lastType.finalResult)
          }
        //should not happen as CompoundExpression doesn't allow this...
        case Nil => valid(Unknown)
      }

      case e: ConstructorReference => withTypedChildren { _ =>
        val className = e.getChild(0).toStringAST
        val classToUse = Try(evaluationContext.getTypeLocator.findType(className)).toOption
        //TODO: validate constructor parameters...
        val clazz = classToUse.flatMap(kl => typeDefinitionSet.get(kl).map(_.clazzName))
        clazz match {
          case Some(typedClass) => Writer(List.empty, TypingResultWithContext(typedClass))
          case None => Writer(List(ConstructionOfUnknown(classToUse)), TypingResultWithContext(Unknown))
        }
      }

      case e: Elvis => withTypedChildren(l => Writer(List.empty, TypingResultWithContext(Typed(l.map(_.typingResult).toSet))))
      //TODO: what should be here?
      case e: FunctionReference => valid(Unknown)

      //TODO: what should be here?
      case e: Identifier => valid(Unknown)
      //TODO: what should be here?
      case e: Indexer => current.stack.headOption match {
        case None =>
          Writer(List(IllegalIndexingOperation), CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Unknown)))
        case Some(result) =>
          typeIndexer(e, result.typingResult)
      }

      case e: Literal => valid(Typed.fromInstance(e.getLiteralValue.getValue))

      case e: InlineList => withTypedChildren { children =>
        val localSupertypeFinder = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.AnySuperclass, true)
        def getSupertype(a: TypingResult, b: TypingResult): TypingResult =
          localSupertypeFinder.commonSupertype(a, b)(NumberTypesPromotionStrategy.ToSupertype)

        //We don't want Typed.empty here, as currently it means it won't validate for any signature
        val elementType = if (children.isEmpty) TypingResultWithContext(Unknown)
          else TypingResultWithContext(children.map(typ => typ.typingResult).reduce(getSupertype))
        Writer(List.empty, TypingResultWithContext(Typed.genericTypeClass[java.util.List[_]](List(elementType.typingResult))))
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

        values.map(typeNode(validationContext, _, current.withoutIntermediateResults)).sequence.flatMap { typedValues =>
          withCombinedIntermediate(typedValues, current) { typedValues =>
            if (literalKeys.size != keys.size) {
              Writer(List(MapWithExpressionKeysError), TypedNode(node, TypingResultWithContext(Unknown)))
            } else {
              val typ = TypedObjectTypingResult(literalKeys.zip(typedValues.map(_.typingResult)))
              Writer(List.empty, TypedNode(node, TypingResultWithContext(typ)))
            }
          }
        }

      case e: MethodReference =>
        extractMethodReference(e, validationContext, node, current)

      case e: OpEQ => checkEqualityLikeOperation(validationContext, e, current, isEquality = true)
      case e: OpNE => checkEqualityLikeOperation(validationContext, e, current, isEquality = false)

      case e: OpAnd => withTwoChildrenOfType[Boolean, Boolean](_ && _)
      case e: OpOr => withTwoChildrenOfType[Boolean, Boolean](_ || _)
      case e: OpGE => withTwoChildrenOfType(MathUtils.greaterOrEqual)
      case e: OpGT => withTwoChildrenOfType(MathUtils.greater)
      case e: OpLE => withTwoChildrenOfType(MathUtils.lesserOrEqual)
      case e: OpLT => withTwoChildrenOfType(MathUtils.lesser)

      case e: OpDec => checkSingleOperandArithmeticOperation(validationContext, e, current)(MathUtils.minus(_, 1))
      case e: OpInc => checkSingleOperandArithmeticOperation(validationContext, e, current)(MathUtils.plus(_, 1))

      case e: OpDivide =>
        val op = (x: Number, y: Number) =>
          if (y.doubleValue() == 0) DivisionByZeroError(e.toStringAST).invalidNel
          else MathUtils.divide(x, y).validNel
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(Some(op))(NumberTypesPromotionStrategy.ForMathOperation)

      case e: OpMinus => withTypedChildren {
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil
          if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) =>
          val supertype = commonSupertypeFinder.commonSupertype(left, right)(NumberTypesPromotionStrategy.ForMathOperation).withoutValue
          val result = operationOnTypesValue[Number, Number, Number](left, right, supertype)(MathUtils.minus(_, _).validNel)
          result.map(TypingResultWithContext(_))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil
          if left == right =>
          Writer(List(OperatorNonNumericError(e.getOperatorName, left)), TypingResultWithContext(Unknown))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil =>
          Writer(List(OperatorMismatchTypeError(e.getOperatorName, left, right)), TypingResultWithContext(Unknown))
        case TypingResultWithContext(left, _) :: Nil if left.canBeSubclassOf(Typed[Number]) =>
          val resultType = left.withoutValue
          val result = operationOnTypesValue[Number, Number](left)(MathUtils.negate).getOrElse(resultType)
          Writer(List.empty, TypingResultWithContext(result))
        case TypingResultWithContext(left, _) :: Nil =>
          Writer(List(OperatorNonNumericError(e.getOperatorName, left)), TypingResultWithContext(Unknown))
        case Nil =>
          Writer(List(EmptyOperatorError(e.getOperatorName)), TypingResultWithContext(Unknown))
        case _ => throw new IllegalStateException("should not happen")
      }
      case e: OpModulus =>
        val op = (x: Number, y: Number) =>
          if (y.doubleValue() == 0) ModuloZeroError(e.toStringAST).invalidNel
          else MathUtils.remainder(x, y).validNel
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(Some(op))(NumberTypesPromotionStrategy.ForMathOperation)
      case e: OpMultiply =>
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(Some(MathUtils.multiply(_, _).validNel))(NumberTypesPromotionStrategy.ForMathOperation)
      case e: OperatorPower =>
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(None)(NumberTypesPromotionStrategy.ForPowerOperation)

      case e: OpPlus => withTypedChildren {
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left == Unknown || right == Unknown =>
          Writer(List.empty, TypingResultWithContext(Unknown))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[String]) || right.canBeSubclassOf(Typed[String]) =>
          val result = operationOnTypesValue[Any, Any, String](left, right, Typed[String])((l, r) => (l.toString + r.toString).validNel)
          result.map(TypingResultWithContext(_))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) =>
          val supertype = commonSupertypeFinder.commonSupertype(left, right)(NumberTypesPromotionStrategy.ForMathOperation).withoutValue
          val result = operationOnTypesValue[Number, Number, Number](left, right, supertype)(MathUtils.plus(_, _).validNel)
          result.map(TypingResultWithContext(_))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil =>
          Writer(List(OperatorMismatchTypeError(e.getOperatorName, left, right)), TypingResultWithContext(Unknown))
        case TypingResultWithContext(left, _) :: Nil if left.canBeSubclassOf(Typed[Number]) =>
          Writer(List.empty, TypingResultWithContext(left))
        case TypingResultWithContext(left, _) :: Nil =>
          Writer(List(OperatorNonNumericError(e.getOperatorName, left)), TypingResultWithContext(Unknown))
        case Nil =>
          Writer(List(EmptyOperatorError(e.getOperatorName)), TypingResultWithContext(Unknown))
        case _ => throw new IllegalStateException("should not happen")
      }
      case e: OperatorBetween => fixed(TypingResultWithContext(Typed[Boolean]))
      case e: OperatorInstanceof => fixed(TypingResultWithContext(Typed[Boolean]))
      case e: OperatorMatches => withChildrenOfType[String](TypingResultWithContext(Typed[Boolean]))
      case e: OperatorNot => withChildrenOfType[Boolean](TypingResultWithContext(Typed[Boolean]))

      case e: Projection => current.stackHead match {
        case None =>
          Writer(List(IllegalProjectionError), CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Unknown)))
        //index, check if can project?
        case Some(iterateType) =>
          extractIterativeType(iterateType.typingResult).flatMap { listType =>
            typeChildren(validationContext, node, current.pushOnStack(listType)) {
              case TypingResultWithContext(result, _) :: Nil =>
                Writer(List.empty, TypingResultWithContext(Typed.genericTypeClass[java.util.List[_]](List(result))))
              case other =>
                Writer(List(IllegalSelectionTypeError(other.map(_.typingResult))), TypingResultWithContext(Unknown))
            }
          }
      }

      case e: PropertyOrFieldReference =>
        current.stackHead.map(head => extractProperty(e, head.typingResult).map(toResult)).getOrElse {
          Writer(List(NonReferenceError(e.toStringAST)), CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Unknown)))
        }
      //TODO: what should be here?
      case e: QualifiedIdentifier => fixed(TypingResultWithContext(Unknown))

      case e: Selection => current.stackHead match {
        case None => Writer(List(IllegalSelectionError), CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Unknown)))
        case Some(iterateType) =>
          extractIterativeType(iterateType.typingResult).flatMap { elementType =>
            typeChildren(validationContext, node, current.pushOnStack(elementType)) {
              case TypingResultWithContext(result, _) :: Nil if result.canBeSubclassOf(Typed[Boolean]) =>
                Writer(List.empty, resolveSelectionTypingResult(e, iterateType, elementType))
              case other =>
                Writer(List(IllegalSelectionTypeError(other.map(_.typingResult))), TypingResultWithContext(Unknown))
            }
          }
      }

      case e: Ternary => withTypedChildren {
        case TypingResultWithContext(condition, _) :: TypingResultWithContext(onTrue, _) :: TypingResultWithContext(onFalse, _) :: Nil =>
          val superType = commonSupertypeFinder.commonSupertype(onTrue, onFalse)(NumberTypesPromotionStrategy.ToSupertype)
          val w = Writer(List.empty[ExpressionParseError], TypingResultWithContext(superType))
          if (!condition.canBeSubclassOf(Typed[Boolean])) {
            w.tell(List(TernaryOperatorNotBooleanError(condition)))
          } else if (superType == Typed.empty) {
            w.tell(List(TernaryOperatorMismatchTypesError(onTrue, onFalse)))
          } else {
            w
          }
        case _ => Writer(List(InvalidTernaryOperator), TypingResultWithContext(Unknown)) // shouldn't happen
      }

      case e: TypeReference =>
        if (staticMethodInvocationsChecking) {
          typeReferenceTyper.typeTypeReference(e)
            .map(typedClass => current.toResult(TypedNode(e, TypingResultWithContext(typedClass, staticContext = true))))
        } else {
          valid(Unknown)
        }

      case e: VariableReference =>
        //only sane way of getting variable name :|
        val name = e.toStringAST.substring(1)
        validationContext.get(name).orElse(current.stackHead.map(_.typingResult).filter(_ => name == "this")) match {
          case Some(result) => valid(result)
          case None => Writer(List(UnresolvedReferenceError(name)), CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Unknown)))
        }
    })
  }

  private def operationOnTypesValue[A, R](typ: TypingResult)
                                         (op: A => R): Option[TypingResult] =
    typ.valueOpt.map(v => Typed.fromInstance(op(v.asInstanceOf[A])))

  private def operationOnTypesValue[A, B, R](left: TypingResult, right: TypingResult, fallbackType: TypingResult)
                                            (op: (A, B) => ValidatedNel[ExpressionParseError, R]): TypingR[TypingResult] =
    (for {
      leftValue <- left.valueOpt
      rightValue <- right.valueOpt
      res = op(leftValue.asInstanceOf[A], rightValue.asInstanceOf[B])
    } yield {
      res.map(Typed.fromInstance)
        .map(Writer(List.empty[ExpressionParseError], _))
        .valueOr(err => Writer(err.toList, fallbackType))
    }).getOrElse(Writer(List.empty[ExpressionParseError], fallbackType))

  //currently there is no better way than to check ast string starting with $ or ^
  private def resolveSelectionTypingResult(node: Selection, parentType: TypingResultWithContext, childElementType: TypingResult) = {
    val isSingleElementSelection = List("$", "^").map(node.toStringAST.startsWith(_)).foldLeft(false)(_ || _)
    if (isSingleElementSelection) TypingResultWithContext(childElementType) else parentType
  }

  private def checkEqualityLikeOperation(validationContext: ValidationContext,
                                         node: Operator,
                                         current: TypingContext,
                                         isEquality: Boolean): TypingR[CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case TypingResultWithContext(TypedObjectWithValue(leftVariable, leftValue), _) ::
        TypingResultWithContext(TypedObjectWithValue(rightVariable, rightValue), _) :: Nil =>
        checkEqualityComparableTypes(leftVariable, rightVariable, node)
          .map(x => TypedObjectWithValue(x.asInstanceOf[TypedClass], leftValue == rightValue ^ !isEquality))
          .map(TypingResultWithContext(_))
      case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil =>
        checkEqualityComparableTypes(left, right, node).map(TypingResultWithContext(_))
      case _ =>
        Writer(List(BadOperatorConstructionError(node.getOperatorName)), TypingResultWithContext(Typed[Boolean])) // shouldn't happen
    }
  }

  private def checkEqualityComparableTypes(left: TypingResult, right: TypingResult, node: Operator): TypingR[TypingResult] = {
    val w = Writer(List.empty[ExpressionParseError], Typed[Boolean])
    if (commonSupertypeFinder.commonSupertype(left, right)(NumberTypesPromotionStrategy.ToSupertype) != Typed.empty) {
      w
    } else
      w.tell(List(OperatorNotComparableError(node.getOperatorName, left, right)))
  }

  private def checkTwoOperandsArithmeticOperation(validationContext: ValidationContext, node: Operator, current: TypingContext)
                                                 (op: Option[(Number, Number) => ValidatedNel[ExpressionParseError, Any]])
                                                 (implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingR[CollectedTypingResult] = {
      typeChildren(validationContext, node, current) {
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) =>
          val supertype = commonSupertypeFinder.commonSupertype(left, right).withoutValue
          val validatedType = op
            .map(operationOnTypesValue[Number, Number, Any](left, right, supertype)(_))
            .getOrElse(Writer(List.empty[ExpressionParseError], supertype))
          validatedType.map(TypingResultWithContext(_))
        case TypingResultWithContext(left, _) :: TypingResultWithContext(right, _) :: Nil =>
          val supertype = commonSupertypeFinder.commonSupertype(left, right).withoutValue
          Writer(List(OperatorMismatchTypeError(node.getOperatorName, left, right)), TypingResultWithContext(supertype))
        case _ =>
          Writer(List(BadOperatorConstructionError(node.getOperatorName)), TypingResultWithContext(Unknown)) // shouldn't happen
      }
    }

  private def checkSingleOperandArithmeticOperation(validationContext: ValidationContext, node: Operator, current: TypingContext)
                                                   (op: Number => Any):
    TypingR[CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case TypingResultWithContext(left, _) :: Nil if left.canBeSubclassOf(Typed[Number]) =>
        val result = operationOnTypesValue[Number, Any](left)(op).getOrElse(left.withoutValue)
        Writer(List.empty, TypingResultWithContext(result))
      case TypingResultWithContext(left, _) :: Nil =>
        Writer(List(OperatorNonNumericError(node.getOperatorName, left)), TypingResultWithContext(left))
      case _ =>
        Writer(List(BadOperatorConstructionError(node.getOperatorName)), TypingResultWithContext(Unknown)) // shouldn't happen
    }
  }

  private def extractProperty(e: PropertyOrFieldReference, t: TypingResult): TypingR[TypingResult] = t match {
    case Unknown =>
      val w = Writer.value[List[ExpressionParseError], TypingResult](Unknown)
      if (methodExecutionForUnknownAllowed)
        w
      else
        w.tell(List(IllegalPropertyAccessError(Unknown)))
    case TypedNull =>
      Writer(List(IllegalPropertyAccessError(TypedNull)), TypedNull)
    case s: SingleTypingResult =>
      extractSingleProperty(e)(s)
    case TypedUnion(possible) =>
      val l = possible.toList.map(single => extractSingleProperty(e)(single))
        .filter(_.written.isEmpty)
        .map(_.value)
      if (l.isEmpty) {
        Writer(List(NoPropertyError(t, e.getName)), Unknown)
      } else
        Writer(List.empty, Typed(l.toSet))
  }

  private def extractMethodReference(reference: MethodReference,
                                     validationContext: ValidationContext,
                                     node: SpelNode,
                                     context: TypingContext): TypingR[CollectedTypingResult] = {
    context.stack match {
      case head :: tail =>
        val isStatic = head.staticContext
        typeChildren(validationContext, node, context.copy(stack = tail)) { typedParams =>
          methodReferenceTyper.typeMethodReference(typer.MethodReference(
            head.typingResult,
            isStatic,
            reference.getName,
            typedParams.map(_.typingResult))
          ).map(TypingResultWithContext(_)) match {
            case Right(x) => Writer(List.empty, x)
            case Left(x) if strictMethodsChecking => Writer(List(x), TypingResultWithContext(Unknown))
            case Left(_) => Writer(List.empty, TypingResultWithContext(Unknown))
          }
        }
      case Nil =>
        Writer(List(InvalidMethodReference(reference.toStringAST)), CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Unknown)))
    }
  }

  @tailrec
  private def extractSingleProperty(e: PropertyOrFieldReference)
                                   (t: SingleTypingResult): TypingR[TypingResult] = {
    t match {
      case typedObjectWithData: TypedObjectWithData =>
        extractSingleProperty(e)(typedObjectWithData.objType)
      case typedClass: TypedClass =>
        propertyTypeBasedOnMethod(typedClass, e)
          .orElse(MapLikePropertyTyper.mapLikeValueType(typedClass))
          .map(Writer(List.empty[ExpressionParseError], _))
          .getOrElse(Writer(List(NoPropertyError(t, e.getName)), Unknown))
      case TypedObjectTypingResult(fields, objType, _) =>
        val typeBasedOnFields = fields.get(e.getName)
        typeBasedOnFields.orElse(propertyTypeBasedOnMethod(objType, e))
          .map(Writer(List.empty[ExpressionParseError], _))
          .getOrElse(Writer(List(NoPropertyError(t, e.getName)), Unknown))
      case dict: TypedDict =>
        dictTyper.typeDictValue(dict, e)
    }
  }

  private def propertyTypeBasedOnMethod(typedClass: TypedClass, e: PropertyOrFieldReference) = {
    typeDefinitionSet.get(typedClass.klass).flatMap(_.getPropertyOrFieldType(e.getName))
  }

  private def extractIterativeType(parent: TypingResult): TypingR[TypingResult] = parent match {
    case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[java.util.Collection[_]]) =>
      Writer(List.empty, tc.objType.params.headOption.getOrElse(Unknown))
    case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[java.util.Map[_, _]]) =>
      Writer(List.empty, TypedObjectTypingResult(List(
        ("key", tc.objType.params.headOption.getOrElse(Unknown)),
        ("value", tc.objType.params.drop(1).headOption.getOrElse(Unknown)))))
    case tc: SingleTypingResult if tc.objType.klass.isArray =>
      Writer(List.empty, tc.objType.params.headOption.getOrElse(Unknown))
    case tc: SingleTypingResult =>
      Writer(List(IllegalProjectionSelectionError(tc)), Unknown)
    //FIXME: what if more results are present?
    case _ => Writer(List.empty, Unknown)
  }

  private def typeChildrenAndReturnFixed(validationContext: ValidationContext, node: SpelNode, current: TypingContext)(result: TypingResultWithContext)
  : TypingR[CollectedTypingResult] = {
    typeChildren(validationContext, node, current)(_ => Writer(List.empty, result))
  }

  private def typeChildren(validationContext: ValidationContext, node: SpelNode, current: TypingContext)
                          (result: List[TypingResultWithContext] => TypingR[TypingResultWithContext])
  : TypingR[CollectedTypingResult] = {
    val data = node.children.map(child => typeNode(validationContext, child, current.withoutIntermediateResults)).sequence
    data.flatMap { collectedChildrenResults =>
      withCombinedIntermediate(collectedChildrenResults, current) { childrenResults =>
        result(childrenResults).map(TypedNode(node, _))
      }
    }
  }

  private def withCombinedIntermediate(intermediate: List[CollectedTypingResult], current: TypingContext)
                                      (result: List[TypingResultWithContext] => TypingR[TypedNode])
  : TypingR[CollectedTypingResult] = {
    val intermediateResultsCombination = Monoid.combineAll(current.intermediateResults :: intermediate.map(_.intermediateResults))
    val intermediateTypes = intermediate.map(_.finalResult)
    result(intermediateTypes).map(CollectedTypingResult.withIntermediateAndFinal(intermediateResultsCombination, _))
  }

  def withDictTyper(dictTyper: SpelDictTyper) =
    new Typer(commonSupertypeFinder, dictTyper, strictMethodsChecking = strictMethodsChecking,
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

// TODO: move intermediateResults into Writer's L
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

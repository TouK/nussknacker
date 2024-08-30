package pl.touk.nussknacker.engine.spel

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel, Writer}
import cats.instances.list._
import cats.instances.map._
import cats.kernel.{Monoid, Semigroup}
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.ast._
import org.springframework.expression.spel.{SpelNode, standard}
import org.springframework.expression.{EvaluationContext, Expression}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression._
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, NumberTypesPromotionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing.Typed.typedListWithElementValues
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.dict.SpelDictTyper
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError._
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.{
  ConstructionOfUnknown,
  NoPropertyError,
  NonReferenceError,
  UnresolvedReferenceError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.OperatorError._
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.PartTypeError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.SelectionProjectionError.{
  IllegalProjectionError,
  IllegalSelectionError,
  IllegalSelectionTypeError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.TernaryOperatorError.{
  InvalidTernaryOperator,
  TernaryOperatorNotBooleanError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.UnsupportedOperationError.{
  ArrayConstructorError,
  BeanReferenceError,
  MapWithExpressionKeysError,
  ModificationError
}
import pl.touk.nussknacker.engine.spel.Typer._
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId
import pl.touk.nussknacker.engine.spel.ast.SpelNodePrettyPrinter
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer
import pl.touk.nussknacker.engine.spel.typer.{MapLikePropertyTyper, MethodReferenceTyper, TypeReferenceTyper}
import pl.touk.nussknacker.engine.util.MathUtils

import scala.jdk.CollectionConverters._
import scala.annotation.tailrec
import scala.reflect.runtime._
import scala.util.{Failure, Success, Try}

private[spel] class Typer(
    dictTyper: SpelDictTyper,
    strictMethodsChecking: Boolean,
    staticMethodInvocationsChecking: Boolean,
    classDefinitionSet: ClassDefinitionSet,
    evaluationContextPreparer: EvaluationContextPreparer,
    methodExecutionForUnknownAllowed: Boolean,
    dynamicPropertyAccessAllowed: Boolean
) extends LazyLogging {

  import ast.SpelAst._

  private lazy val evaluationContext: EvaluationContext =
    evaluationContextPreparer.prepareEvaluationContext(Context(""), Map.empty)

  private val methodReferenceTyper = new MethodReferenceTyper(classDefinitionSet, methodExecutionForUnknownAllowed)

  private lazy val typeReferenceTyper = new TypeReferenceTyper(evaluationContext, classDefinitionSet)

  type TypingR[T]       = Writer[List[ExpressionParseError], T]
  type NodeTypingResult = TypingR[CollectedTypingResult]

  def typeExpression(
      expr: Expression,
      ctx: ValidationContext
  ): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val (errors, result) = doTypeExpression(expr, ctx)
    NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(result))
  }

  def doTypeExpression(
      expr: Expression,
      ctx: ValidationContext
  ): (List[ExpressionParseError], CollectedTypingResult) = {
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
        (Nil, CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(TypedNull)))
    }
  }

  private def typeExpression(
      spelExpression: standard.SpelExpression,
      ctx: ValidationContext
  ): (List[ExpressionParseError], CollectedTypingResult) = {
    val ast                       = spelExpression.getAST
    val (errors, collectedResult) = typeNode(ctx, ast, TypingContext(List.empty, Map.empty)).run
    logger.whenTraceEnabled {
      val printer = new SpelNodePrettyPrinter(n =>
        collectedResult.intermediateResults.get(SpelNodeId(n)).map(_.display).getOrElse("NOT_TYPED")
      )
      logger.trace(s"typed nodes: ${printer.print(ast)}, errors: ${errors.mkString(", ")}")
    }
    (errors, collectedResult)
  }

  private def typeNode(
      validationContext: ValidationContext,
      node: SpelNode,
      current: TypingContext
  ): NodeTypingResult = {

    def toNodeResult(typ: TypingResult) = current.toResult(TypedNode(node, TypingResultWithContext(typ)))

    def validNodeResult(typ: TypingResult) = valid(toNodeResult(typ))

    def invalidNodeResult(err: ExpressionParseError) = invalid(err).map(toNodeResult)

    val withTypedChildren = typeChildren(validationContext, node, current) _

    def fixedWithNewCurrent(newCurrent: TypingContext) =
      typeChildrenAndReturnFixed(validationContext, node, newCurrent) _

    val fixed = fixedWithNewCurrent(current)

    def withChildrenOfType[Parts: universe.TypeTag](result: TypingResult) = {
      val w = valid(result)
      withTypedChildren {
        case list if list.forall(_.canBeSubclassOf(Typed.fromDetailedType[Parts])) => w
        case _                                                                     => w.tell(List(PartTypeError))
      }
    }

    def withTwoChildrenOfType[A: universe.TypeTag, R: universe.TypeTag](op: (A, A) => R) = {
      val castExpectedType = CastTypedValue[A]()
      val resultType       = Typed.fromDetailedType[R]
      withTypedChildren {
        case castExpectedType(left) :: castExpectedType(right) :: Nil =>
          val typeFromOp = for {
            leftValue  <- left.valueOpt
            rightValue <- right.valueOpt
            res = op(leftValue, rightValue)
          } yield Typed.fromInstance(res)
          valid(typeFromOp.getOrElse(resultType))
        case _ =>
          invalid(PartTypeError, fallbackType = resultType)
      }
    }

    def catchUnexpectedErrors(block: => NodeTypingResult): NodeTypingResult = Try(block) match {
      case Success(value) =>
        value
      case Failure(e) =>
        throw new SpelCompilationException(node, e)
    }

    def typeUnion(e: Indexer, union: TypedUnion): NodeTypingResult = {
      val typedPossibleTypes = union.possibleTypes.map(possibleType => typeIndexer(e, possibleType))

      val typingResult = typedPossibleTypes.sequence
        .map(_.map(_.finalResult.typingResult))
        .map(typingResults => Typed(typingResults))
      typingResult.map(toNodeResult)
    }

    def typeFieldNameReferenceOnRecord(indexString: String, record: TypedObjectTypingResult): TypingR[TypingResult] = {
      val fieldIndexedByLiteralStringOpt = record.fields.find(_._1 == indexString)
      fieldIndexedByLiteralStringOpt.map(f => valid(f._2)).getOrElse {
        if (dynamicPropertyAccessAllowed) valid(Unknown) else invalid(NoPropertyError(record, indexString))
      }
    }

    def typeIndexerOnRecord(indexer: Indexer, record: TypedObjectTypingResult) = {
      withTypedChildren {
        case TypedObjectWithValue(_, indexString: String) :: Nil =>
          // Children are typed in a non-obvious way in case of PropertyOrFieldReference
          indexer.children match {
            case (ref: PropertyOrFieldReference) :: Nil => typeFieldNameReferenceOnRecord(ref.getName, record)
            case _                                      => typeFieldNameReferenceOnRecord(indexString, record)
          }
        case indexKey :: Nil if indexKey.canBeSubclassOf(Typed[String]) =>
          if (dynamicPropertyAccessAllowed) valid(Unknown) else invalid(DynamicPropertyAccessError)
        case _ :: Nil =>
          indexer.children match {
            case (ref: PropertyOrFieldReference) :: Nil => typeFieldNameReferenceOnRecord(ref.getName, record)
            case _ => if (dynamicPropertyAccessAllowed) valid(Unknown) else invalid(DynamicPropertyAccessError)
          }
        case _ =>
          invalid(IllegalIndexingOperation)
      }
    }

    @tailrec
    def typeIndexer(e: Indexer, typingResult: TypingResult): NodeTypingResult = {
      typingResult match {
        case TypedClass(clazz, param :: Nil)
            if clazz.isAssignableFrom(classOf[java.util.List[_]]) || clazz.isAssignableFrom(classOf[Array[Object]]) =>
          // TODO: validate indexer key - the only valid key is an integer - but its more complicated with references
          validNodeResult(param)
        case TypedClass(clazz, keyParam :: valueParam :: Nil) if clazz.isAssignableFrom(classOf[java.util.Map[_, _]]) =>
          validNodeResult(valueParam)
        case d: TypedDict                    => dictTyper.typeDictValue(d, e).map(toNodeResult)
        case union: TypedUnion               => typeUnion(e, union)
        case TypedTaggedValue(underlying, _) => typeIndexer(e, underlying)
        case r: TypedObjectTypingResult      => typeIndexerOnRecord(e, r)
        // TODO: add indexing on strings
        // TODO: how to handle other cases?
        case TypedNull =>
          invalidNodeResult(IllegalIndexingOperation)
        case TypedObjectWithValue(underlying, _) => typeIndexer(e, underlying)
        case _ =>
          val w = validNodeResult(Unknown)
          if (dynamicPropertyAccessAllowed) w else w.tell(List(DynamicPropertyAccessError))
      }
    }

    catchUnexpectedErrors(node match {

      case e: Assign =>
        invalidNodeResult(ModificationError)
      case e: BeanReference =>
        invalidNodeResult(BeanReferenceError)
      case e: CompoundExpression =>
        e.children match {
          case first :: rest =>
            val validatedLastType = rest.foldLeft(typeNode(validationContext, first, current)) { case (prevW, next) =>
              prevW.flatMap { prevResult =>
                typeNode(validationContext, next, current.pushOnStack(prevResult))
              }
            }
            validatedLastType.map { lastType =>
              CollectedTypingResult(
                lastType.intermediateResults + (SpelNodeId(e) -> lastType.finalResult),
                lastType.finalResult
              )
            }
          // should not happen as CompoundExpression doesn't allow this...
          case Nil => validNodeResult(Unknown)
        }

      case e: ConstructorReference =>
        // TODO: validate constructor parameters...
        withTypedChildren { _ =>
          val className    = e.getChild(0).toStringAST
          val classToUse   = Try(evaluationContext.getTypeLocator.findType(className)).toOption
          val typingResult = classToUse.flatMap(kl => classDefinitionSet.get(kl).map(_.clazzName))
          typingResult match {
            case Some(tc @ TypedClass(_, _)) =>
              if (isArrayConstructor(e)) {
                invalid(ArrayConstructorError)
              } else {
                valid(tc)
              }
            case Some(_) =>
              throw new IllegalStateException(
                "Illegal construction of ConstructorReference. Expected nonempty typing result of TypedClass or empty typing result"
              )
            case None => invalid(ConstructionOfUnknown(classToUse))
          }
        }
      case e: Elvis =>
        withTypedChildren {
          case first :: second :: Nil => valid(Typed(first, second))
          case other =>
            throw new IllegalStateException(
              s"Illegal construction of elvis. Found ${other.size} children, but 2 children expected"
            )
        }
      // TODO: what should be here?
      case e: FunctionReference => validNodeResult(Unknown)

      // TODO: what should be here?
      case e: Identifier => validNodeResult(Unknown)
      // TODO: what should be here?
      case e: Indexer =>
        current.stackHead
          .map(valid)
          .getOrElse(invalid(IllegalIndexingOperation))
          .flatMap(typeIndexer(e, _))

      case e: Literal => validNodeResult(Typed.fromInstance(e.getLiteralValue.getValue))

      case e: InlineList =>
        withTypedChildren { children =>
          def getSupertype(a: TypingResult, b: TypingResult): TypingResult =
            CommonSupertypeFinder.Default.commonSupertype(a, b)

          val elementType           = if (children.isEmpty) Unknown else children.reduce(getSupertype).withoutValue
          val childrenCombinedValue = children.flatMap(_.valueOpt).asJava

          valid(typedListWithElementValues(elementType, childrenCombinedValue))
        }

      case e: InlineMap =>
        val zipped = e.children.zipWithIndex
        val keys   = zipped.filter(_._2 % 2 == 0).map(_._1)
        val values = zipped.filter(_._2 % 2 == 1).map(_._1)
        val literalKeys = keys
          .collect {
            case a: PropertyOrFieldReference => a.getName
            case b: StringLiteral            => b.getLiteralValue.getValue.toString
          }

        typeChildrenNodes(validationContext, node, values, current) { typedValues =>
          if (literalKeys.size != keys.size) {
            invalid(MapWithExpressionKeysError)
          } else {
            valid(Typed.record(literalKeys.zip(typedValues)))
          }
        }
      case e: MethodReference =>
        extractMethodReference(e, validationContext, node, current)

      case e: OpEQ => checkEqualityLikeOperation(validationContext, e, current, isEquality = true)
      case e: OpNE => checkEqualityLikeOperation(validationContext, e, current, isEquality = false)

      case e: OpAnd => withTwoChildrenOfType[Boolean, Boolean](_ && _)
      case e: OpOr  => withTwoChildrenOfType[Boolean, Boolean](_ || _)
      case e: OpGE  => withTwoChildrenOfType(MathUtils.greaterOrEqual)
      case e: OpGT  => withTwoChildrenOfType(MathUtils.greater)
      case e: OpLE  => withTwoChildrenOfType(MathUtils.lesserOrEqual)
      case e: OpLT  => withTwoChildrenOfType(MathUtils.lesser)

      case e: OpDec => checkSingleOperandArithmeticOperation(validationContext, e, current)(MathUtils.minus(_, 1))
      case e: OpInc => checkSingleOperandArithmeticOperation(validationContext, e, current)(MathUtils.plus(_, 1))

      case e: OpDivide =>
        val op = (x: Number, y: Number) =>
          if (y.doubleValue() == 0) Invalid(DivisionByZeroError(e.toStringAST))
          else Valid(MathUtils.divide(x, y))
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(Some(op))(
          NumberTypesPromotionStrategy.ForMathOperation
        )

      case e: OpMinus =>
        withTypedChildren {
          case left :: right :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) =>
            val fallback = NumberTypesPromotionStrategy.ForMathOperation.promote(left, right)
            operationOnTypesValue[Number, Number, Number](left, right, fallback)((n1, n2) =>
              Valid(MathUtils.minus(n1, n2))
            )
          case left :: right :: Nil if left == right =>
            invalid(OperatorNonNumericError(e.getOperatorName, left))
          case left :: right :: Nil =>
            invalid(OperatorMismatchTypeError(e.getOperatorName, left, right))
          case left :: Nil if left.canBeSubclassOf(Typed[Number]) =>
            val resultType = left.withoutValue
            val result     = operationOnTypesValue[Number, Number](left)(MathUtils.negate).getOrElse(resultType)
            valid(result)
          case left :: Nil =>
            invalid(OperatorNonNumericError(e.getOperatorName, left))
          case Nil =>
            invalid(EmptyOperatorError(e.getOperatorName))
          case _ => throw new IllegalStateException("should not happen")
        }
      case e: OpModulus =>
        val op = (x: Number, y: Number) =>
          if (y.doubleValue() == 0) Invalid(ModuloZeroError(e.toStringAST))
          else Valid(MathUtils.remainder(x, y))
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(Some(op))(
          NumberTypesPromotionStrategy.ForMathOperation
        )
      case e: OpMultiply =>
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(
          Some((n1, n2) => Valid(MathUtils.multiply(n1, n2)))
        )(NumberTypesPromotionStrategy.ForMathOperation)
      case e: OperatorPower =>
        checkTwoOperandsArithmeticOperation(validationContext, e, current)(None)(
          NumberTypesPromotionStrategy.ForPowerOperation
        )

      case e: OpPlus =>
        withTypedChildren {
          case left :: right :: Nil if left == Unknown || right == Unknown =>
            valid(Unknown)
          case left :: right :: Nil if left.canBeSubclassOf(Typed[String]) || right.canBeSubclassOf(Typed[String]) =>
            operationOnTypesValue[Any, Any, String](left, right, Typed[String])((l, r) =>
              Valid(l.toString + r.toString)
            )
          case left :: right :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) =>
            val fallback = NumberTypesPromotionStrategy.ForMathOperation.promote(left, right)
            operationOnTypesValue[Number, Number, Number](left, right, fallback)((n1, n2) =>
              Valid(MathUtils.plus(n1, n2))
            )
          case left :: right :: Nil =>
            invalid(OperatorMismatchTypeError(e.getOperatorName, left, right))
          case left :: Nil if left.canBeSubclassOf(Typed[Number]) =>
            valid(left)
          case left :: Nil =>
            invalid(OperatorNonNumericError(e.getOperatorName, left))
          case Nil =>
            invalid(EmptyOperatorError(e.getOperatorName))
          case _ => throw new IllegalStateException("should not happen")
        }
      case e: OperatorBetween    => fixed(Typed[Boolean])
      case e: OperatorInstanceof => fixed(Typed[Boolean])
      case e: OperatorMatches    => withChildrenOfType[String](Typed[Boolean])
      case e: OperatorNot        => withChildrenOfType[Boolean](Typed[Boolean])

      case e: Projection =>
        for {
          iterateType <- current.stackHead.map(valid).getOrElse(invalid(IllegalProjectionError))
          elementType <- extractIterativeType(iterateType)
          result <- typeChildren(validationContext, node, current.pushOnStack(elementType)) {
            case result :: Nil =>
              // Limitation: projection on an iterative type makes it loses it's known value,
              // as properly determining it would require evaluating the projection expression for each element (likely working on the AST)
              valid(Typed.genericTypeClass[java.util.List[_]](List(result)))
            case other =>
              invalid(IllegalSelectionTypeError(other))
          }
        } yield result

      case e: PropertyOrFieldReference =>
        current.stackHead
          .map(extractProperty(e, _))
          .getOrElse(invalid(NonReferenceError(e.toStringAST)))
          .map(toNodeResult)
      // TODO: what should be here?
      case e: QualifiedIdentifier => fixed(Unknown)

      case e: Selection =>
        for {
          iterateType <- current.stackHead.map(valid).getOrElse(invalid(IllegalSelectionError))
          elementType <- extractIterativeType(iterateType)
          selectionType = resolveSelectionTypingResult(e, iterateType, elementType)
          result <- typeChildren(validationContext, node, current.pushOnStack(elementType)) {
            case result :: Nil if result.canBeSubclassOf(Typed[Boolean]) =>
              valid(selectionType)
            case other =>
              invalid(IllegalSelectionTypeError(other), selectionType)
          }
        } yield result
      case e: Ternary =>
        withTypedChildren {
          case condition :: onTrue :: onFalse :: Nil =>
            for {
              _ <- Option(condition)
                .filter(_.canBeSubclassOf(Typed[Boolean]))
                .map(valid)
                .getOrElse(invalid(TernaryOperatorNotBooleanError(condition)))
            } yield CommonSupertypeFinder.Default.commonSupertype(onTrue, onFalse)
          case _ =>
            invalid(InvalidTernaryOperator) // shouldn't happen
        }

      case e: TypeReference =>
        if (staticMethodInvocationsChecking) {
          typeReferenceTyper
            .typeTypeReference(e)
            .map(typedClass => current.toResult(TypedNode(e, TypingResultWithContext.withStaticContext(typedClass))))
        } else {
          validNodeResult(Unknown)
        }

      case e: VariableReference =>
        // only sane way of getting variable name :|
        val name = e.toStringAST.substring(1)
        validationContext
          .get(name)
          .orElse(current.stackHead.filter(_ => name == "this"))
          .map(valid)
          .getOrElse(invalid(UnresolvedReferenceError(name)))
          .map(toNodeResult)
    })
  }

  private def operationOnTypesValue[A, R](typ: TypingResult)(op: A => R): Option[TypingResult] =
    typ.valueOpt.map(v => Typed.fromInstance(op(v.asInstanceOf[A])))

  private def operationOnTypesValue[A, B, R](left: TypingResult, right: TypingResult, fallbackType: TypingResult)(
      op: (A, B) => Validated[ExpressionParseError, R]
  ): TypingR[TypingResult] =
    (for {
      leftValue  <- left.valueOpt
      rightValue <- right.valueOpt
      res = op(leftValue.asInstanceOf[A], rightValue.asInstanceOf[B])
    } yield {
      res
        .map(Typed.fromInstance)
        .map(valid)
        .valueOr(invalid(_, fallbackType))
    }).getOrElse(valid(fallbackType))

  // currently there is no better way than to check ast string starting with $ or ^
  private def resolveSelectionTypingResult(
      node: Selection,
      parentType: TypingResult,
      childElementType: TypingResult
  ) = {
    val isSingleElementSelection = List("$", "^").map(node.toStringAST.startsWith(_)).foldLeft(false)(_ || _)

    if (isSingleElementSelection)
      childElementType
    else {
      // Limitation: selection from an iterative type makes it loses it's known value,
      // as properly determining it would require evaluating the selection expression for each element (likely working on the AST)
      parentType match {
        case tc: SingleTypingResult if tc.displayObjType.canBeSubclassOf(Typed[java.util.Collection[_]]) =>
          tc.withoutValue
        case tc: SingleTypingResult if tc.displayObjType.canBeSubclassOf(Typed[java.util.Map[_, _]]) =>
          Typed.record(Map.empty)
        case _ =>
          parentType
      }
    }
  }

  private def isArrayConstructor(constructorReference: ConstructorReference): Boolean = {
    val dimensionsField = constructorReference.getClass.getDeclaredField("isArrayConstructor")
    dimensionsField.setAccessible(true)
    dimensionsField.get(constructorReference).asInstanceOf[Boolean]
  }

  private def checkEqualityLikeOperation(
      validationContext: ValidationContext,
      node: Operator,
      current: TypingContext,
      isEquality: Boolean
  ): TypingR[CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case TypedObjectWithValue(leftVariable, leftValue) ::
          TypedObjectWithValue(rightVariable, rightValue) :: Nil =>
        checkEqualityComparableTypes(leftVariable, rightVariable, node)
          .map(x => TypedObjectWithValue(x.asInstanceOf[TypedClass], leftValue == rightValue ^ !isEquality))
      case left :: right :: Nil =>
        checkEqualityComparableTypes(left, right, node)
      case _ =>
        invalid(BadOperatorConstructionError(node.getOperatorName), fallbackType = Typed[Boolean])
    }
  }

  private def checkEqualityComparableTypes(
      left: TypingResult,
      right: TypingResult,
      node: Operator
  ): TypingR[TypingResult] = {
    val w = valid(Typed[Boolean])
    CommonSupertypeFinder.Intersection
      .commonSupertypeOpt(left, right)
      .map(_ => w)
      .getOrElse(w.tell(List(OperatorNotComparableError(node.getOperatorName, left, right))))
  }

  private def checkTwoOperandsArithmeticOperation(
      validationContext: ValidationContext,
      node: Operator,
      current: TypingContext
  )(
      op: Option[(Number, Number) => Validated[ExpressionParseError, Any]]
  )(implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingR[CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case left :: right :: Nil if left.canBeSubclassOf(Typed[Number]) && right.canBeSubclassOf(Typed[Number]) =>
        val fallback = numberPromotionStrategy.promote(left, right)
        op
          .map(operationOnTypesValue[Number, Number, Any](left, right, fallback)(_))
          .getOrElse(valid(fallback))
      case left :: right :: Nil =>
        val fallback = CommonSupertypeFinder.Default.commonSupertype(left, right).withoutValue
        invalid(OperatorMismatchTypeError(node.getOperatorName, left, right), fallbackType = fallback)
      case _ =>
        invalid(BadOperatorConstructionError(node.getOperatorName)) // shouldn't happen
    }
  }

  private def checkSingleOperandArithmeticOperation(
      validationContext: ValidationContext,
      node: Operator,
      current: TypingContext
  )(op: Number => Any): TypingR[CollectedTypingResult] = {
    typeChildren(validationContext, node, current) {
      case left :: Nil if left.canBeSubclassOf(Typed[Number]) =>
        val result = operationOnTypesValue[Number, Any](left)(op).getOrElse(left.withoutValue)
        valid(result)
      case left :: Nil =>
        invalid(OperatorNonNumericError(node.getOperatorName, left), fallbackType = left)
      case _ =>
        invalid(BadOperatorConstructionError(node.getOperatorName)) // shouldn't happen
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
      invalid(IllegalPropertyAccessError(TypedNull), fallbackType = TypedNull)
    case s: SingleTypingResult =>
      extractSingleProperty(e)(s)
    case union: TypedUnion =>
      val l = union.possibleTypes
        .map(single => extractSingleProperty(e)(single))
        .filter(_.written.isEmpty)
        .map(_.value)
      NonEmptyList
        .fromList(l)
        .map(nel => valid(Typed(nel)))
        .getOrElse(invalid(NoPropertyError(t, e.getName)))
  }

  private def extractMethodReference(
      reference: MethodReference,
      validationContext: ValidationContext,
      node: SpelNode,
      context: TypingContext
  ): TypingR[CollectedTypingResult] = {
    (context.stack match {
      case head :: tail =>
        valid((head, tail))
      case Nil =>
        invalid(InvalidMethodReference(reference.toStringAST))
          .map(TypingResultWithContext(_))
          .map((_, context.stack))
    }).flatMap { case (head, tail) =>
      typeChildren(validationContext, node, context.copy(stack = tail)) { typedParams =>
        methodReferenceTyper.typeMethodReference(
          typer.MethodReference(head.typingResult, head.staticContext, reference.getName, typedParams)
        ) match {
          case Right(x)                         => valid(x)
          case Left(x) if strictMethodsChecking => invalid(x)
          case Left(_)                          => valid(Unknown)
        }
      }
    }
  }

  @tailrec
  private def extractSingleProperty(e: PropertyOrFieldReference)(t: SingleTypingResult): TypingR[TypingResult] = {
    t match {
      case typedObjectWithData: TypedObjectWithData =>
        extractSingleProperty(e)(typedObjectWithData.displayObjType)
      case typedClass: TypedClass =>
        propertyTypeBasedOnMethod(typedClass, typedClass, e)
          .orElse(MapLikePropertyTyper.mapLikeValueType(typedClass))
          .map(valid)
          .getOrElse(invalid(NoPropertyError(t, e.getName)))
      case recordType @ TypedObjectTypingResult(fields, objType, _) =>
        val typeBasedOnFields = fields.get(e.getName)
        typeBasedOnFields
          .orElse(propertyTypeBasedOnMethod(objType, recordType, e))
          .map(valid)
          .getOrElse(invalid(NoPropertyError(t, e.getName)))
      case dict: TypedDict =>
        dictTyper.typeDictValue(dict, e)
    }
  }

  private def propertyTypeBasedOnMethod(
      clazz: TypedClass,
      invocationTarget: TypingResult,
      e: PropertyOrFieldReference
  ) = {
    classDefinitionSet.get(clazz.klass).flatMap(_.getPropertyOrFieldType(invocationTarget, e.getName))
  }

  private def extractIterativeType(parent: TypingResult): TypingR[TypingResult] = parent match {
    case tc: SingleTypingResult if tc.displayObjType.canBeSubclassOf(Typed[java.util.Collection[_]]) =>
      valid(tc.displayObjType.params.headOption.getOrElse(Unknown))
    case tc: SingleTypingResult if tc.displayObjType.canBeSubclassOf(Typed[java.util.Map[_, _]]) =>
      valid(
        Typed.record(
          Map(
            "key"   -> tc.displayObjType.params.headOption.getOrElse(Unknown),
            "value" -> tc.displayObjType.params.drop(1).headOption.getOrElse(Unknown)
          )
        )
      )
    case tc: SingleTypingResult =>
      invalid(IllegalProjectionSelectionError(tc))
    // FIXME: what if more results are present?
    case _ => valid(Unknown)
  }

  private def typeChildrenAndReturnFixed(validationContext: ValidationContext, node: SpelNode, current: TypingContext)(
      result: TypingResult
  ): TypingR[CollectedTypingResult] = {
    typeChildren(validationContext, node, current)(_ => valid(result))
  }

  private def typeChildren(validationContext: ValidationContext, node: SpelNode, current: TypingContext)(
      result: List[TypingResult] => TypingR[TypingResult]
  ): TypingR[CollectedTypingResult] = {
    typeChildrenNodes(validationContext, node, node.children, current)(result)
  }

  private def typeChildrenNodes(
      validationContext: ValidationContext,
      node: SpelNode,
      children: List[SpelNode],
      current: TypingContext
  )(result: List[TypingResult] => TypingR[TypingResult]): TypingR[CollectedTypingResult] = {
    children.map(typeNode(validationContext, _, current.withoutIntermediateResults)).sequence.flatMap {
      collectedChildrenResults =>
        val intermediateResultsCombination =
          Monoid.combineAll(current.intermediateResults :: collectedChildrenResults.map(_.intermediateResults))
        val intermediateTypes = collectedChildrenResults.map(_.finalResult)
        result(intermediateTypes.map(_.typingResult))
          .map(TypingResultWithContext(_))
          .map(TypedNode(node, _))
          .map(CollectedTypingResult.withIntermediateAndFinal(intermediateResultsCombination, _))
    }
  }

  private def valid[T](value: T): TypingR[T] = Writer(List.empty[ExpressionParseError], value)

  private def invalid[T](err: ExpressionParseError, fallbackType: TypingResult = Unknown): TypingR[TypingResult] =
    Writer(List(err), fallbackType)

  def withDictTyper(dictTyper: SpelDictTyper) =
    new Typer(
      dictTyper,
      strictMethodsChecking = strictMethodsChecking,
      staticMethodInvocationsChecking,
      classDefinitionSet,
      evaluationContextPreparer,
      methodExecutionForUnknownAllowed,
      dynamicPropertyAccessAllowed
    )

}

object Typer {

  def default(
      classLoader: ClassLoader,
      expressionConfig: ExpressionConfigDefinition,
      spelDictTyper: SpelDictTyper,
      classDefinitionSet: ClassDefinitionSet
  ): Typer = {
    val evaluationContextPreparer = EvaluationContextPreparer.default(classLoader, expressionConfig)

    new Typer(
      spelDictTyper,
      expressionConfig.strictMethodsChecking,
      expressionConfig.staticMethodInvocationsChecking,
      classDefinitionSet,
      evaluationContextPreparer,
      expressionConfig.methodExecutionForUnknownAllowed,
      expressionConfig.dynamicPropertyAccessAllowed
    )
  }

  // This Semigroup is used in combining `intermediateResults: Map[SpelNodeId, TypingResult]` in Typer.
  // If there is no bug in Typer, collisions shouldn't happen
  implicit def notAcceptingMergingSemigroup: Semigroup[TypingResultWithContext] = new Semigroup[TypingResultWithContext]
    with LazyLogging {

    override def combine(x: TypingResultWithContext, y: TypingResultWithContext): TypingResultWithContext = {
      assert(x == y, s"Types not matching during combination of types for spel nodes: $x != $y")
      // merging the same types is not bad but it is a warning that sth went wrong e.g. typer typed something more than one time
      // or spel node's identity is broken
      logger.warn(s"Merging same types: $x for the same nodes. This shouldn't happen")
      x
    }

  }

  case class TypingResultWithContext private (typingResult: TypingResult, staticContext: Boolean) {

    def display: String = typingResult.display

  }

  object TypingResultWithContext {

    def apply(typingResult: TypingResult): TypingResultWithContext =
      new TypingResultWithContext(typingResult, staticContext = false)

    def withStaticContext(typingResult: TypingResult) = new TypingResultWithContext(typingResult, staticContext = true)

    private def apply(typingResult: TypingResult, staticContext: Boolean) =
      new TypingResultWithContext(typingResult, staticContext)

  }

  /**
    * It contains stack of types for recognition of nested node type.
    * intermediateResults are all results that we can collect for intermediate nodes
    */
  private case class TypingContext(
      stack: List[TypingResultWithContext],
      intermediateResults: Map[SpelNodeId, TypingResultWithContext]
  ) {

    def pushOnStack(typingResult: TypingResult): TypingContext =
      copy(stack = TypingResultWithContext(typingResult) :: stack)

    def pushOnStack(typingResult: CollectedTypingResult): TypingContext =
      TypingContext(typingResult.finalResult :: stack, intermediateResults ++ typingResult.intermediateResults)

    def stackHead: Option[TypingResult] = stack.headOption.map(_.typingResult)

    def withoutIntermediateResults: TypingContext = copy(intermediateResults = Map.empty)

    def toResult(finalNode: TypedNode): CollectedTypingResult =
      CollectedTypingResult(intermediateResults + (finalNode.nodeId -> finalNode.typ), finalNode.typ)

  }

  class SpelCompilationException(node: SpelNode, cause: Throwable)
      extends RuntimeException(
        s"Can't compile SpEL expression: `${node.toStringAST}`, message: `${cause.getMessage}`.",
        cause
      )

}

private[spel] case class TypedNode(nodeId: SpelNodeId, typ: TypingResultWithContext)

private[spel] object TypedNode {

  def apply(node: SpelNode, typ: TypingResultWithContext): TypedNode =
    TypedNode(SpelNodeId(node), typ)

}

// TODO: move intermediateResults into Writer's L
private[spel] case class CollectedTypingResult(
    intermediateResults: Map[SpelNodeId, TypingResultWithContext],
    finalResult: TypingResultWithContext
) {

  def typingInfo: SpelExpressionTypingInfo = SpelExpressionTypingInfo(
    intermediateResults.map(intermediateResult => (intermediateResult._1 -> intermediateResult._2.typingResult)),
    finalResult.typingResult
  )

}

private[spel] object CollectedTypingResult {

  def withEmptyIntermediateResults(finalResult: TypingResultWithContext): CollectedTypingResult =
    CollectedTypingResult(Map.empty, finalResult)

  def withIntermediateAndFinal(
      intermediateResults: Map[SpelNodeId, TypingResultWithContext],
      finalNode: TypedNode
  ): CollectedTypingResult = {
    CollectedTypingResult(intermediateResults + (finalNode.nodeId -> finalNode.typ), finalNode.typ)
  }

}

case class SpelExpressionTypingInfo(intermediateResults: Map[SpelNodeId, TypingResult], typingResult: TypingResult)
    extends ExpressionTypingInfo

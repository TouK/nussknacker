package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, Validated, ValidatedNel, Writer}
import cats.data.Validated.{Invalid, Valid}
import cats.instances.list._
import cats.instances.map._
import cats.kernel.{Monoid, Semigroup}
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression.{EvaluationContext, Expression}
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.{standard, SpelNode}
import org.springframework.expression.spel.ast._
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression._
import pl.touk.nussknacker.engine.api.typed.{AssignabilityDeterminer, ConversionStrategy}
import pl.touk.nussknacker.engine.api.typed.ConversionStrategy.NoConversion
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, NumberTypesPromotionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.typed.typing.Typed.typedListWithElementValues
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.dict.SpelDictTyper
import pl.touk.nussknacker.engine.expression.{IndexBasedTextRange, NullExpression}
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.{
  ArgumentTypeError,
  PartTypeError,
  SpelExpressionTypingError,
  SpelExpressionTypingErrorWithTextRange
}
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.IllegalOperationError._
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.MissingObjectError._
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.OperatorError._
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.SelectionProjectionError.{
  IllegalProjectionError,
  IllegalSelectionError,
  IllegalSelectionTypeError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.TernaryOperatorError.{
  InvalidTernaryOperator,
  TernaryOperatorNotBooleanError
}
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingError.UnsupportedOperationError.{
  ArrayConstructorError,
  BeanReferenceError,
  MapWithExpressionKeysError,
  ModificationError
}
import pl.touk.nussknacker.engine.spel.SpelTyper._
import pl.touk.nussknacker.engine.spel.ast.SpelAst.{RichSpelNode, SpelNodeId}
import pl.touk.nussknacker.engine.spel.ast.SpelNodePrettyPrinter
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer
import pl.touk.nussknacker.engine.spel.parser.{
  CompositeExpressionsWithTextRanges,
  ExpressionWithTextRange,
  SingleExpressionWithTextRange
}
import pl.touk.nussknacker.engine.spel.typer.{MapLikePropertyTyper, MethodReferenceTyper, TypeReferenceTyper}
import pl.touk.nussknacker.engine.util.MathUtils

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.reflect.runtime._
import scala.util.{Failure, Success, Try}

private[spel] class SpelTyper(
    dictTyper: SpelDictTyper,
    strictMethodsChecking: Boolean,
    staticMethodInvocationsChecking: Boolean,
    classDefinitionSet: ClassDefinitionSet,
    evaluationContextPreparer: EvaluationContextPreparer,
    anyMethodExecutionForUnknownAllowed: Boolean,
    dynamicPropertyAccessAllowed: Boolean,
    absentVariableReferenceAllowed: Boolean
) extends LazyLogging {

  import ast.SpelAst._

  private lazy val evaluationContext: EvaluationContext =
    evaluationContextPreparer.prepareEvaluationContext(Context.dummy, Map.empty)

  private val methodReferenceTyper = new MethodReferenceTyper(classDefinitionSet, anyMethodExecutionForUnknownAllowed)

  private lazy val typeReferenceTyper = new TypeReferenceTyper(evaluationContext, classDefinitionSet)

  private type TypingR[T]       = Writer[List[SpelExpressionTypingErrorWithTextRange], T]
  private type NodeTypingResult = TypingR[CollectedTypingResult]

  def typeExpression(
      expr: ExpressionWithTextRange,
      ctx: ValidationContext,
      flavour: SpelFlavour
  ): ValidatedNel[SpelExpressionTypingErrorWithTextRange, CollectedTypingResult] = {
    val (errors, result) = typeExpressionWithTextRange(expr, ctx, flavour)
    NonEmptyList.fromList(errors).map(Invalid(_)).getOrElse(Valid(result))
  }

  def typeExpressionWithTextRange(
      expressionWithTextRange: ExpressionWithTextRange,
      ctx: ValidationContext,
      flavour: SpelFlavour
  ): (List[SpelExpressionTypingErrorWithTextRange], CollectedTypingResult) = {
    expressionWithTextRange match {
      case composite: CompositeExpressionsWithTextRanges =>
        val (allShiftedErrors, allShiftedIntermediateResults) =
          composite.getChildExpressionsWithTextRanges.toList.foldRight(
            (List.empty[SpelExpressionTypingErrorWithTextRange], Map.empty[SpelNodeId, TypingResultWithContext])
          ) { case (expressionWithTextRange, (errorsAcc, intermediateResultsAcc)) =>
            val (errors, collectedTypingResult) =
              doTypeSingleExpression(expressionWithTextRange.getExpression, ctx, expressionWithTextRange.getTextRange)
            (errors ::: errorsAcc, intermediateResultsAcc ++ collectedTypingResult.intermediateResults)
          }
        (
          allShiftedErrors,
          CollectedTypingResult(
            allShiftedIntermediateResults,
            TypingResultWithContext(Typed[String]) // composite expressions are used in the string context
          )
        )
      case single: SingleExpressionWithTextRange =>
        val (errors, collectedTypingResult) = doTypeSingleExpression(single.getExpression, ctx, single.getTextRange)
        val collectedTypingResultWithFinalTypingResultAdjustedForTemplates =
          if (flavour == SpelFlavour.Template)
            collectedTypingResult.withFinalTypingResult(Typed[String])
          else
            collectedTypingResult
        (errors, collectedTypingResultWithFinalTypingResultAdjustedForTemplates)
    }
  }

  private def doTypeSingleExpression(
      expr: Expression,
      ctx: ValidationContext,
      textRange: IndexBasedTextRange
  ): (List[SpelExpressionTypingErrorWithTextRange], CollectedTypingResult) =
    expr match {
      case e: standard.SpelExpression =>
        typeExpression(e, ctx, textRange)
      case _: LiteralExpression =>
        (Nil, CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(Typed[String])))
      case _: NullExpression =>
        (Nil, CollectedTypingResult.withEmptyIntermediateResults(TypingResultWithContext(TypedNull)))
      case e: CompositeStringExpression =>
        throw new IllegalStateException(s"${e.getClass.getName} in place where only single expressions where expected")
    }

  private def typeExpression(
      spelExpression: standard.SpelExpression,
      ctx: ValidationContext,
      textRange: IndexBasedTextRange
  ): (List[SpelExpressionTypingErrorWithTextRange], CollectedTypingResult) = {
    val ast                       = spelExpression.getAST
    val (errors, collectedResult) = typeNode(ctx, ast, TypingContext(List.empty, Map.empty)).run
    logger.whenTraceEnabled {
      val printer = new SpelNodePrettyPrinter(n =>
        collectedResult.intermediateResults.get(n.textRange).map(_.display).getOrElse("NOT_TYPED")
      )
      logger.trace(s"typed nodes: ${printer.print(ast)}, errors: ${errors.mkString(", ")}")
    }
    val shiftedErrors = errors.map(_.shiftTextRangeRight(textRange.start))
    val shiftedIntermediateResults = collectedResult.intermediateResults.map { case (spelNodeId, intermediateResult) =>
      spelNodeId.shiftRight(textRange.start) -> intermediateResult
    }
    (shiftedErrors, CollectedTypingResult(shiftedIntermediateResults, collectedResult.finalResult))
  }

  private def typeNode(
      validationContext: ValidationContext,
      node: SpelNode,
      current: TypingContext
  ): NodeTypingResult = {

    implicit class TypingResultOps(typ: TypingResult) {

      def toNodeResult: CollectedTypingResult = current.toResult(TypedNode(node, TypingResultWithContext(typ)))

      def validNodeResult: TypingR[CollectedTypingResult] = valid(typ.toNodeResult)

      def validTypingResult: TypingR[TypingResult] = Writer(List.empty[SpelExpressionTypingErrorWithTextRange], typ)

    }

    implicit class SpelExpressionTypingErrorOps(err: SpelExpressionTypingError) {

      def withTextRange: SpelExpressionTypingErrorWithTextRange =
        SpelExpressionTypingErrorWithTextRange(err, node.textRange)

      def invalidNodeResult: TypingR[CollectedTypingResult] = err.invalidTypingResult().map(_.toNodeResult)

      def invalidTypingResult(fallbackType: TypingResult = Unknown): TypingR[TypingResult] =
        Writer(List(err.withTextRange), fallbackType)

    }

    val withTypedChildren = typeChildren(validationContext, node, current) _

    def withChildrenOfType[Parts: universe.TypeTag](result: TypingResult) = {
      val w = result.validTypingResult
      withTypedChildren {
        case list if list.forall(_.canBeLooselyAssignedTo(Typed.fromDetailedType[Parts])) => w
        case _ => w.tell(List(PartTypeError.withTextRange))
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
          typeFromOp.getOrElse(resultType).validTypingResult
        case _ =>
          PartTypeError.invalidTypingResult(fallbackType = resultType)
      }
    }

    def getRecordValueType(record: TypedObjectTypingResult) = {
      val fieldTypes = record.withoutValue.fields.values
      CommonSupertypeFinder.Default.superTypeOfTypes(fieldTypes)
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
      typingResult.map(_.toNodeResult)
    }

    def typeFieldNameReferenceOnRecord(
        indexString: String,
        record: TypedObjectTypingResult
    ): TypingR[TypingResult] = {
      val fieldIndexedByLiteralStringOpt = record.fields.find(_._1 == indexString)
      fieldIndexedByLiteralStringOpt
        .map(f => f._2.validTypingResult)
        .getOrElse {
          if (dynamicPropertyAccessAllowed) {
            getRecordValueType(record).validTypingResult
          } else NoPropertyError(record, indexString).invalidTypingResult()
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
        case indexKey :: Nil if indexKey.canBeLooselyAssignedTo(Typed[String]) =>
          if (dynamicPropertyAccessAllowed) getRecordValueType(record).validTypingResult
          else
            record.runtimeObjType.params match {
              case _ :: value :: Nil if record.runtimeObjType.klass == MapClass =>
                value.validTypingResult
              case _ => Unknown.validTypingResult
            }
        case e :: Nil =>
          indexer.children match {
            case (ref: PropertyOrFieldReference) :: Nil => typeFieldNameReferenceOnRecord(ref.getName, record)
            case _ =>
              if (dynamicPropertyAccessAllowed) Unknown.validTypingResult
              else NoPropertyTypeError(record, e).invalidTypingResult()
          }
        case _ =>
          IllegalIndexingOperation.invalidTypingResult()
      }
    }

    @tailrec
    def typeIndexer(e: Indexer, typingResult: TypingResult): NodeTypingResult = {
      typingResult match {
        case TypedClass(clazz, param :: Nil)
            if clazz.isAssignableFrom(ListClass) || clazz.isAssignableFrom(ArrayClass) =>
          // TODO: validate indexer key - the only valid key is an integer - but its more complicated with references
          withTypedChildren(_ => param.validTypingResult)
        case TypedClass(clazz, keyParam :: valueParam :: Nil) if clazz.isAssignableFrom(MapClass) =>
          withTypedChildren {
            // Spel implementation of map indexer (in class org.springframework.expression.spel.ast.Indexer, line 154) tries to convert
            // indexer to key type of map, but this conversion can be accomplished only if key type of map is known to spel.
            // Currently .asMap extension is implemented in such a way, that spel does not know key type of the resulting map
            // (that is when spel evaluates this expression it only knows that it got map, but does not know its type parameters).
            // It would be hard to change implementation of .asMap extension so we partially turn off this feature of indexer conversion
            // by allowing in typing only situations when map key type and indexer type are the same (though we have to allow
            // indexing with unknown type)
            case indexKey :: Nil if indexKey.canBeAssignedTo(keyParam)(NoConversion) => valueParam.validTypingResult
            case _ => IllegalIndexingOperation.invalidTypingResult()
          }
        case d: TypedDict =>
          dictTyper
            .typeDictValue(d, e)
            .map(_.toNodeResult)
            .mapWritten(_.map(_.withTextRange))
        case union: TypedUnion               => typeUnion(e, union)
        case TypedTaggedValue(underlying, _) => typeIndexer(e, underlying)
        case r: TypedObjectTypingResult      => typeIndexerOnRecord(e, r)
        // TODO: add indexing on strings
        // TODO: how to handle other cases?
        case TypedNull =>
          IllegalIndexingOperation.invalidNodeResult
        case TypedObjectWithValue(underlying, _) => typeIndexer(e, underlying)
        case u: Unknown =>
          withTypedChildren(_ => u.validTypingResult)
        case _: TypedClass =>
          val w = withTypedChildren(_ => Unknown.validTypingResult)
          if (dynamicPropertyAccessAllowed) w else w.tell(List(DynamicPropertyAccessError.withTextRange))
      }
    }

    def operationOnTypeValue[A, R](typ: TypingResult)(op: A => R): Option[TypingResult] =
      typ.valueOpt.map(v => Typed.fromInstance(op(v.asInstanceOf[A])))

    def operationOnTypesValue[A, B, R](left: TypingResult, right: TypingResult, fallbackType: TypingResult)(
        op: (A, B) => Validated[SpelExpressionTypingError, R]
    ): TypingR[TypingResult] =
      (for {
        leftValue  <- left.valueOpt
        rightValue <- right.valueOpt
        res        <- Try(op(leftValue.asInstanceOf[A], rightValue.asInstanceOf[B])).toOption
      } yield {
        res
          .map(Typed.fromInstance)
          .map(_.validTypingResult)
          .valueOr(_.invalidTypingResult(fallbackType))
      }).getOrElse(fallbackType.validTypingResult)

    def checkEqualityLikeOperation(node: Operator, isEquality: Boolean): TypingR[CollectedTypingResult] = {
      typeChildren(validationContext, node, current) {
        case TypedObjectWithValue(leftVariable, leftValue) ::
            TypedObjectWithValue(rightVariable, rightValue) :: Nil =>
          checkEqualityComparableTypes(leftVariable, rightVariable, node)
            .map(x => TypedObjectWithValue(x.asInstanceOf[TypedClass], leftValue == rightValue ^ !isEquality))
        case left :: right :: Nil =>
          checkEqualityComparableTypes(left, right, node)
        case _ =>
          BadOperatorConstructionError(node.getOperatorName).invalidTypingResult(fallbackType = Typed[Boolean])
      }
    }

    def checkEqualityComparableTypes(
        left: TypingResult,
        right: TypingResult,
        node: Operator
    ): TypingR[TypingResult] = {
      val w = Typed[Boolean].validTypingResult
      CommonSupertypeFinder.Intersection
        .commonSupertypeOpt(left, right)
        .map(_ => w)
        .getOrElse(w.tell(List(OperatorNotComparableError(node.getOperatorName, left, right).withTextRange)))
    }

    def checkTwoOperandsArithmeticOperation(node: Operator)(
        op: Option[(Number, Number) => Validated[SpelExpressionTypingError, Any]]
    )(implicit numberPromotionStrategy: NumberTypesPromotionStrategy): TypingR[CollectedTypingResult] = {
      typeChildren(validationContext, node, current) {
        case left :: right :: Nil
            if left.canBeLooselyAssignedTo(Typed[Number]) && right.canBeLooselyAssignedTo(Typed[Number]) =>
          val fallback = numberPromotionStrategy.promote(left, right)
          op
            .map(operationOnTypesValue[Number, Number, Any](left, right, fallback)(_))
            .getOrElse(fallback.validTypingResult)
        case left :: right :: Nil =>
          val fallback = CommonSupertypeFinder.Default.commonSupertype(left, right).withoutValue
          OperatorMismatchTypeError(node.getOperatorName, left, right).invalidTypingResult(fallbackType = fallback)
        case _ =>
          BadOperatorConstructionError(node.getOperatorName).invalidTypingResult() // shouldn't happen
      }
    }

    def checkSingleOperandArithmeticOperation(node: Operator)(op: Number => Any): TypingR[CollectedTypingResult] = {
      typeChildren(validationContext, node, current) {
        case left :: Nil if left.canBeLooselyAssignedTo(Typed[Number]) =>
          val result = operationOnTypeValue[Number, Any](left)(op).getOrElse(left.withoutValue)
          result.validTypingResult
        case left :: Nil =>
          OperatorNonNumericError(node.getOperatorName, left).invalidTypingResult(fallbackType = left)
        case _ =>
          BadOperatorConstructionError(node.getOperatorName).invalidTypingResult() // shouldn't happen
      }
    }

    def extractProperty(e: PropertyOrFieldReference, t: TypingResult): TypingR[TypingResult] = t match {
      case u: Unknown =>
        val w = Writer.value[List[SpelExpressionTypingErrorWithTextRange], TypingResult](u)
        if (anyMethodExecutionForUnknownAllowed) {
          unknownPropertyTypeBasedOnMethod(e, u)
            .map(_.validTypingResult)
            .getOrElse(w)
        } else {
          // we allow some methods to be used on unknown
          unknownPropertyTypeBasedOnMethod(e, u)
            .map(_.validTypingResult)
            .getOrElse(w.tell(List(IllegalPropertyAccessError(u).withTextRange)))
        }
      case TypedNull =>
        IllegalPropertyAccessError(TypedNull).invalidTypingResult(fallbackType = TypedNull)
      case s: SingleTypingResult =>
        extractSingleProperty(e)(s)
      case union: TypedUnion =>
        val l = union.possibleTypes
          .map(single => extractSingleProperty(e)(single))
          .filter(_.written.isEmpty)
          .map(_.value)
        NonEmptyList
          .fromList(l)
          .map(nel => Typed(nel).validTypingResult)
          .getOrElse(NoPropertyError(t, e.getName).invalidTypingResult())
    }

    def extractMethodReference(reference: MethodReference): TypingR[CollectedTypingResult] = {
      (current.stack match {
        case head :: tail =>
          valid((head, tail))
        case Nil =>
          InvalidMethodReference(reference.toStringAST)
            .invalidTypingResult()
            .map(TypingResultWithContext(_))
            .map((_, current.stack))
      }).flatMap { case (head, tail) =>
        typeChildren(validationContext, node, current.copy(stack = tail)) { typedParams =>
          methodReferenceTyper.typeMethodReference(
            typer.MethodReference(head.typingResult, head.staticContext, reference.getName, typedParams)
          ) match {
            case Right(x)                         => x.validTypingResult
            case Left(x) if strictMethodsChecking => x.invalidTypingResult()
            case Left(_)                          => Unknown.validTypingResult
          }
        }
      }
    }

    @tailrec
    def extractSingleProperty(e: PropertyOrFieldReference)(t: SingleTypingResult): TypingR[TypingResult] = {
      t match {
        case typedObjectWithData: TypedObjectWithData =>
          extractSingleProperty(e)(typedObjectWithData.runtimeObjType)
        case typedClass: TypedClass =>
          propertyTypeBasedOnMethod(typedClass, typedClass, e)
            .orElse(MapLikePropertyTyper.mapLikeValueType(typedClass))
            .map(_.validTypingResult)
            .getOrElse(NoPropertyError(t, e.getName).invalidTypingResult())
        case recordType @ TypedObjectTypingResult(fields, objType, _) =>
          val typeBasedOnFields = fields.get(e.getName)
          typeBasedOnFields
            .orElse(propertyTypeBasedOnMethod(objType, recordType, e))
            .map(_.validTypingResult)
            .getOrElse(NoPropertyError(t, e.getName).invalidTypingResult())
        case dict: TypedDict =>
          dictTyper
            .typeDictValue(dict, e)
            .mapWritten(_.map(_.withTextRange))
      }
    }

    // It returns iterative type and type after conversion if it was necessary
    def extractIterativeType(collectionType: TypingResult): TypingR[(TypingResult, TypingResult)] = {
      val collectionResult = Option(collectionType).collect {
        case tc: SingleTypingResult
            if tc.runtimeObjType.canBeStrictlyAssignedTo(Typed[java.util.Collection[_]]) ||
              tc.runtimeObjType.klass.isArray =>
          // For Collection and array we don't do the conversion - so we return input collectionType instead
          valid(tc.runtimeObjType.params.headOption.getOrElse(Unknown), collectionType)
      }
      lazy val mapResult: Option[TypingR[(TypingResult, TypingResult)]] = AssignabilityDeterminer
        .typeAfterPotentialConversion(collectionType, Typed[java.util.Map[_, _]])(ConversionStrategy.Loose)
        .toOption
        .collect { case singleTypeAfterPotentialConversion: SingleTypingResult =>
          (singleTypeAfterPotentialConversion.runtimeObjType, singleTypeAfterPotentialConversion)
        }
        .collect { case (TypedClass(_, keyParam :: valueParam :: Nil), typeAfterPotentialConversion) =>
          valid(
            (
              Typed.record(
                Map(
                  "key"   -> keyParam,
                  "value" -> valueParam
                )
              ),
              typeAfterPotentialConversion
            )
          )
        }
      collectionResult orElse mapResult getOrElse (collectionType match {
        case tc: SingleTypingResult =>
          IllegalProjectionSelectionError(tc).invalidTypingResult().map((_, collectionType))
        // FIXME: what if more results are present?
        case _ => valid((Unknown, collectionType))
      })
    }

    def projectionResult(iterableType: TypingResult, elementType: TypingResult): TypingR[TypingResult] =
      iterableType.withoutValue match {
        case tc: TypedClass if tc.klass.isArray => Typed.genericTypeClass(tc.klass, List(elementType)).validTypingResult
        case _ => Typed.genericTypeClass[java.util.List[_]](List(elementType)).validTypingResult
      }

    catchUnexpectedErrors(node match {

      case e: Assign =>
        ModificationError.invalidNodeResult
      case e: BeanReference =>
        BeanReferenceError.invalidNodeResult
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
                lastType.intermediateResults + (e.textRange -> lastType.finalResult),
                lastType.finalResult
              )
            }
          // should not happen as CompoundExpression doesn't allow this...
          case Nil => Unknown.validNodeResult
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
                ArrayConstructorError.invalidTypingResult()
              } else {
                tc.validTypingResult
              }
            case Some(_) =>
              throw new IllegalStateException(
                "Illegal construction of ConstructorReference. Expected nonempty typing result of TypedClass or empty typing result"
              )
            case None => ConstructionOfUnknown(classToUse).invalidTypingResult()
          }
        }
      case e: Elvis =>
        withTypedChildren {
          case first :: second :: Nil => Typed(first, second).validTypingResult
          case other =>
            throw new IllegalStateException(
              s"Illegal construction of elvis. Found ${other.size} children, but 2 children expected"
            )
        }
      // The only valid case in NU seems to be a call to a method defined in global variables
      case e: FunctionReference =>
        val functionName = e.toStringAST.stripPrefix("#").takeWhile(_ != '(')
        validationContext
          .get(functionName)
          .map {
            case TypedClass(clazz, List()) if classOf[java.lang.reflect.Method].isAssignableFrom(clazz) =>
              Unknown.validNodeResult
            case other =>
              IllegalInvocationError(other).invalidNodeResult
          }
          .getOrElse(UnresolvedReferenceError(functionName).invalidNodeResult)

      // TODO: what should be here?
      case e: Identifier => Unknown.validNodeResult
      // TODO: what should be here?
      case e: Indexer =>
        current.stackHead
          .map(_.validTypingResult)
          .getOrElse(IllegalIndexingOperation.invalidTypingResult())
          .flatMap(typeIndexer(e, _))

      case e: Literal => Typed.fromInstance(e.getLiteralValue.getValue).validNodeResult

      case e: InlineList =>
        withTypedChildren { children =>
          def getSupertype(a: TypingResult, b: TypingResult): TypingResult =
            CommonSupertypeFinder.Default.commonSupertype(a, b)

          val elementType           = if (children.isEmpty) Unknown else children.reduce(getSupertype).withoutValue
          val childrenCombinedValue = children.flatMap(_.valueOpt)
          if (children.size == childrenCombinedValue.size) {
            // Combine values only if all children have one; partial results would be misleading.
            typedListWithElementValues(elementType, childrenCombinedValue.asJava).validTypingResult
          } else {
            Typed.genericTypeClass(ListClass, List(elementType)).validTypingResult
          }
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
            MapWithExpressionKeysError.invalidTypingResult()
          } else {
            Typed.record(literalKeys.zip(typedValues)).validTypingResult
          }
        }
      case e: MethodReference => extractMethodReference(e)

      case e: OpEQ => checkEqualityLikeOperation(e, isEquality = true)
      case e: OpNE => checkEqualityLikeOperation(e, isEquality = false)

      case e: OpAnd => withTwoChildrenOfType[Boolean, Boolean](_ && _)
      case e: OpOr  => withTwoChildrenOfType[Boolean, Boolean](_ || _)
      case e: OpGE  => withTwoChildrenOfType(MathUtils.greaterOrEqual)
      case e: OpGT  => withTwoChildrenOfType(MathUtils.greater)
      case e: OpLE  => withTwoChildrenOfType(MathUtils.lesserOrEqual)
      case e: OpLT  => withTwoChildrenOfType(MathUtils.lesser)

      case e: OpDec => checkSingleOperandArithmeticOperation(e)(MathUtils.minus(_, 1))
      case e: OpInc => checkSingleOperandArithmeticOperation(e)(MathUtils.plus(_, 1))

      case e: OpDivide =>
        val op = (x: Number, y: Number) =>
          if (y.doubleValue() == 0) Invalid(DivisionByZeroError(e.toStringAST))
          else Valid(MathUtils.divide(x, y))
        checkTwoOperandsArithmeticOperation(e)(Some(op))(
          NumberTypesPromotionStrategy.ForMathOperation
        )

      case e: OpMinus =>
        withTypedChildren {
          case left :: right :: Nil
              if left.canBeLooselyAssignedTo(Typed[Number]) && right.canBeLooselyAssignedTo(Typed[Number]) =>
            val fallback = NumberTypesPromotionStrategy.ForMathOperation.promote(left, right)
            operationOnTypesValue[Number, Number, Number](left, right, fallback)((n1, n2) =>
              Valid(MathUtils.minus(n1, n2))
            )
          case left :: right :: Nil if left == right =>
            OperatorNonNumericError(e.getOperatorName, left).invalidTypingResult()
          case left :: right :: Nil =>
            OperatorMismatchTypeError(e.getOperatorName, left, right).invalidTypingResult()
          case left :: Nil if left.canBeLooselyAssignedTo(Typed[Number]) =>
            val resultType = left.withoutValue
            val result     = operationOnTypeValue[Number, Number](left)(MathUtils.negate).getOrElse(resultType)
            result.validTypingResult
          case left :: Nil =>
            OperatorNonNumericError(e.getOperatorName, left).invalidTypingResult()
          case Nil =>
            EmptyOperatorError(e.getOperatorName).invalidTypingResult()
          case _ => throw new IllegalStateException("should not happen")
        }
      case e: OpModulus =>
        val op = (x: Number, y: Number) =>
          if (y.doubleValue() == 0) Invalid(ModuloZeroError(e.toStringAST))
          else Valid(MathUtils.remainder(x, y))
        checkTwoOperandsArithmeticOperation(e)(Some(op))(
          NumberTypesPromotionStrategy.ForMathOperation
        )
      case e: OpMultiply =>
        checkTwoOperandsArithmeticOperation(e)(
          Some((n1, n2) => Valid(MathUtils.multiply(n1, n2)))
        )(NumberTypesPromotionStrategy.ForMathOperation)
      case e: OperatorPower =>
        checkTwoOperandsArithmeticOperation(e)(None)(
          NumberTypesPromotionStrategy.ForPowerOperation
        )

      case e: OpPlus =>
        withTypedChildren {
          case left :: right :: Nil if left == Unknown || right == Unknown =>
            Unknown.validTypingResult
          case left :: right :: Nil
              if left.canBeLooselyAssignedTo(Typed[String]) || right.canBeLooselyAssignedTo(Typed[String]) =>
            operationOnTypesValue[Any, Any, String](left, right, Typed[String])((l, r) =>
              Valid(l.toString + r.toString)
            )
          case left :: right :: Nil
              if left.canBeLooselyAssignedTo(Typed[Number]) && right.canBeLooselyAssignedTo(Typed[Number]) =>
            val fallback = NumberTypesPromotionStrategy.ForMathOperation.promote(left, right)
            operationOnTypesValue[Number, Number, Number](left, right, fallback)((n1, n2) =>
              Valid(MathUtils.plus(n1, n2))
            )
          case left :: right :: Nil =>
            OperatorMismatchTypeError(e.getOperatorName, left, right).invalidTypingResult()
          case left :: Nil if left.canBeLooselyAssignedTo(Typed[Number]) =>
            left.validTypingResult
          case left :: Nil =>
            OperatorNonNumericError(e.getOperatorName, left).invalidTypingResult()
          case Nil =>
            EmptyOperatorError(e.getOperatorName).invalidTypingResult()
          case _ => throw new IllegalStateException("should not happen")
        }
      case e: OperatorBetween    => Typed[Boolean].validNodeResult
      case e: OperatorInstanceof => Typed[Boolean].validNodeResult
      case e: OperatorMatches    => withChildrenOfType[String](Typed[Boolean])
      case e: OperatorNot        => withChildrenOfType[Boolean](Typed[Boolean])

      case e: Projection =>
        for {
          iterateType <- current.stackHead
            .map(_.validTypingResult)
            .getOrElse(IllegalProjectionError.invalidTypingResult())
          elementTypeWithTypeAfterConversion <- extractIterativeType(iterateType)
          (elementType, _) = elementTypeWithTypeAfterConversion
          result <- typeChildren(validationContext, node, current.pushOnStack(elementType)) {
            case result :: Nil =>
              projectionResult(iterateType, result)
            case other =>
              IllegalSelectionTypeError(other).invalidTypingResult()
          }
        } yield result

      case e: PropertyOrFieldReference =>
        current.stackHead
          .map(extractProperty(e, _))
          .map(mapErrorAndCheckMethodsIfPropertyNotExists)
          .getOrElse(NonReferenceError(e.toStringAST).invalidTypingResult())
          .map(_.toNodeResult)
      // TODO: what should be here?
      case e: QualifiedIdentifier => Unknown.validNodeResult

      case e: Selection =>
        for {
          iterateType <- current.stackHead
            .map(_.validTypingResult)
            .getOrElse(IllegalSelectionError.invalidTypingResult())
          elementTypeWithTypeAfterConversion <- extractIterativeType(iterateType)
          (elementType, typeAfterConversion) = elementTypeWithTypeAfterConversion
          selectionType                      = resolveSelectionTypingResult(e, typeAfterConversion, elementType)
          result <- typeChildren(validationContext, node, current.pushOnStack(elementType)) {
            case result :: Nil if result.canBeLooselyAssignedTo(Typed[Boolean]) =>
              selectionType.validTypingResult
            case other =>
              IllegalSelectionTypeError(other).invalidTypingResult(fallbackType = selectionType)
          }
        } yield result
      case e: Ternary =>
        withTypedChildren {
          case condition :: onTrue :: onFalse :: Nil =>
            for {
              _ <- Option(condition)
                .filter(_.canBeLooselyAssignedTo(Typed[Boolean]))
                .map(_.validTypingResult)
                .getOrElse(TernaryOperatorNotBooleanError(condition).invalidTypingResult())
            } yield CommonSupertypeFinder.Default.commonSupertype(onTrue, onFalse)
          case _ =>
            InvalidTernaryOperator.invalidTypingResult() // shouldn't happen
        }

      case e: TypeReference =>
        if (staticMethodInvocationsChecking) {
          typeReferenceTyper
            .typeTypeReference(e)
            .map(typedClass => current.toResult(TypedNode(e, TypingResultWithContext.withStaticContext(typedClass))))
            .mapWritten(_.map(_.withTextRange))
        } else {
          Unknown.validNodeResult
        }

      case e: VariableReference =>
        // only sane way of getting variable name :|
        val name = e.toStringAST.substring(1)
        validationContext
          .get(name)
          .orElse(current.stackHead.filter(_ => name == "this"))
          .map(_.validTypingResult)
          .getOrElse(
            if (absentVariableReferenceAllowed) Unknown.validTypingResult
            else UnresolvedReferenceError(name).invalidTypingResult()
          )
          .map(_.toNodeResult)
    })
  }

  // currently there is no better way than to check ast string starting with $ or ^
  private def resolveSelectionTypingResult(
      node: Selection,
      collectionType: TypingResult,
      childElementType: TypingResult
  ) = {
    val isSingleElementSelection = List("$", "^").map(node.toStringAST.startsWith(_)).foldLeft(false)(_ || _)
    if (isSingleElementSelection)
      childElementType
    else {
      collectionType match {
        case single: SingleTypingResult =>
          single.runtimeObjType // Filtering can change set of fields in record, so we have to return underlying type
        case other =>
          other
      }
    }
  }

  private def isArrayConstructor(constructorReference: ConstructorReference): Boolean = {
    val dimensionsField = constructorReference.getClass.getDeclaredField("isArrayConstructor")
    dimensionsField.setAccessible(true)
    dimensionsField.get(constructorReference).asInstanceOf[Boolean]
  }

  private def unknownPropertyTypeBasedOnMethod(e: PropertyOrFieldReference, u: Unknown): Option[TypingResult] =
    classDefinitionSet.unknown.flatMap(_.getPropertyOrFieldType(u, e.getName))

  private def propertyTypeBasedOnMethod(
      clazz: TypedClass,
      invocationTarget: TypingResult,
      e: PropertyOrFieldReference
  ) = {
    classDefinitionSet.get(clazz.klass).flatMap(_.getPropertyOrFieldType(invocationTarget, e.getName))
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

  private def mapErrorAndCheckMethodsIfPropertyNotExists(typing: TypingR[TypingResult]): TypingR[TypingResult] =
    typing.mapWritten(_.map(_.mapError {
      case e: NoPropertyError =>
        methodReferenceTyper.typeMethodReference(
          typer.MethodReference(e.typ, isStatic = false, e.property, Nil)
        ) match {
          // Right is not mapped because of: pl.touk.nussknacker.engine.spel.Typer.propertyTypeBasedOnMethod and
          // pl.touk.nussknacker.engine.spel.internal.propertyAccessors.NoParamMethodPropertyAccessor
          // Methods without parameters can be treated as properties.
          case Left(me: ArgumentTypeError) => me
          case _                           => e
        }
      case e => e
    }))

  private def valid[T](value: T): TypingR[T] = Writer(List.empty[SpelExpressionTypingErrorWithTextRange], value)

  def withDictTyper(dictTyper: SpelDictTyper) =
    new SpelTyper(
      dictTyper,
      strictMethodsChecking = strictMethodsChecking,
      staticMethodInvocationsChecking,
      classDefinitionSet,
      evaluationContextPreparer,
      anyMethodExecutionForUnknownAllowed,
      dynamicPropertyAccessAllowed,
      absentVariableReferenceAllowed
    )

  def withAbsentVariableReferenceAllowed(value: Boolean): SpelTyper =
    new SpelTyper(
      dictTyper,
      strictMethodsChecking,
      staticMethodInvocationsChecking,
      classDefinitionSet,
      evaluationContextPreparer,
      anyMethodExecutionForUnknownAllowed,
      dynamicPropertyAccessAllowed,
      absentVariableReferenceAllowed = value
    )

}

object SpelTyper {

  def default(
      classLoader: ClassLoader,
      expressionConfig: ExpressionConfigDefinition,
      spelDictTyper: SpelDictTyper,
      classDefinitionSet: ClassDefinitionSet,
      absentVariableReferenceAllowed: Boolean
  ): SpelTyper = {
    val evaluationContextPreparer = EvaluationContextPreparer.default(classLoader, expressionConfig, classDefinitionSet)

    new SpelTyper(
      spelDictTyper,
      expressionConfig.strictMethodsChecking,
      expressionConfig.staticMethodInvocationsChecking,
      classDefinitionSet,
      evaluationContextPreparer,
      expressionConfig.methodExecutionForUnknownAllowed,
      expressionConfig.dynamicPropertyAccessAllowed,
      absentVariableReferenceAllowed
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
    TypedNode(node.textRange, typ)

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

  def withFinalTypingResult(newFinalTypingResult: TypingResult): CollectedTypingResult =
    copy(finalResult = finalResult.copy(typingResult = newFinalTypingResult))

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

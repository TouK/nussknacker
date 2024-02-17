package pl.touk.nussknacker.engine.compile

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import io.circe.Json
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, FatalUnknownError}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import javax.validation.constraints.NotBlank
import scala.concurrent.Future

object validationHelpers {

  object SimpleStringSource extends SourceFactory with UnboundedStreamComponent {
    @MethodToInvoke(returnType = classOf[String])
    def create(): api.process.Source = null
  }

  object SimpleStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(
        @ParamName("stringVal")
        @AdditionalVariables(value =
          Array(new api.AdditionalVariable(name = "additionalVar1", clazz = classOf[String]))
        )
        stringVal: LazyParameter[String]
    ) = {}

  }

  object SimpleStringService extends Service {
    @MethodToInvoke
    def invoke(@ParamName("stringParam") param: String): Future[String] = ???
  }

  object Enricher extends Service {
    @MethodToInvoke
    def invoke(): Future[String] = ???
  }

  object AddingVariableStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@OutputVariableName variableName: String)(implicit nodeId: NodeId) = {
      ContextTransformation
        .definedBy(_.withVariable(variableName, Typed[String], paramName = None))
        .withComponentLogic(null)
    }

  }

  object ClearingContextStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute() = {
      ContextTransformation
        .definedBy(ctx => Valid(ctx.clearVariables))
        .withComponentLogic(null)
    }

  }

  object ProducingTupleTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("numberOfFields") numberOfFields: Int, @OutputVariableName variableName: String)(
        implicit nodeId: NodeId
    ): ContextTransformation = {
      ContextTransformation
        .definedBy { context =>
          val newType = Typed.record((1 to numberOfFields).map { i =>
            s"field$i" -> Typed[String]
          }.toMap)
          context.withVariable(variableName, newType, paramName = None)
        }
        .withComponentLogic(null)
    }

  }

  object UnionTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(
        @BranchParamName("key") @NotBlank keyByBranchId: Map[String, LazyParameter[
          CharSequence
        ]], // key is only for runtime purpose
        @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[_]],
        @OutputVariableName variableName: String
    ): JoinContextTransformation = {
      ContextTransformation.join
        .definedBy { contexts =>
          val newType = Typed.record(contexts.toSeq.map { case (branchId, _) =>
            branchId -> valueByBranchId(branchId).returnType
          }.toMap)
          Valid(ValidationContext(Map(variableName -> newType)))
        }
        .withComponentLogic(null)
    }

  }

  object UnionTransformerWithMainBranch extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(
        @BranchParamName("key") keyByBranchId: Map[String, LazyParameter[_]], // key is only for runtime purpose
        @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[_]],
        @BranchParamName("mainBranch") mainBranch: Map[String, Boolean],
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId): JoinContextTransformation = {
      ContextTransformation.join
        .definedBy { contexts =>
          val (mainBranches, joinedBranches) = contexts.partition { case (branchId, _) =>
            mainBranch(branchId)
          }
          if (mainBranches.size != 1) {
            Invalid(FatalUnknownError("Should be exact one main branch")).toValidatedNel
          } else {
            val mainBranchContext = mainBranches.head._2

            val newType = Typed.record(joinedBranches.map { case (branchId, _) =>
              branchId -> valueByBranchId(branchId).returnType
            })

            mainBranchContext.withVariable(variableName, newType, paramName = None)
          }
        }
        .withComponentLogic(null)
    }

  }

  object NonEndingCustomNodeReturningTransformation extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): ContextTransformation = {
      ContextTransformation
        .definedBy(ctx => Valid(ctx.clearVariables))
        .withComponentLogic(null)
    }

    override def canBeEnding: Boolean = false
  }

  object NonEndingCustomNodeReturningUnit extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): Unit = {}

    override def canBeEnding: Boolean = false
  }

  object OptionalEndingStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): Unit = {}

    override def canBeEnding: Boolean = true
  }

  object AddingVariableOptionalEndingStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String, @OutputVariableName variableName: String): Unit = {}

    override def canBeEnding: Boolean = true
  }

  object DynamicNoBranchParameterJoinTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(implicit nodeId: NodeId): JoinContextTransformation = {
      ContextTransformation.join
        .definedBy(contexts => {
          contexts.values.toList.distinct match {
            case Nil => Invalid(CustomNodeError("At least one validation context needed", Option.empty)).toValidatedNel
            case one :: Nil => Valid(one)
            case _          => Invalid(CustomNodeError("Validation contexts do not match", Option.empty)).toValidatedNel
          }
        })
        .withComponentLogic(null)
    }

  }

  object MissingParamHandleDynamicComponent$ extends EagerService with SingleInputDynamicComponent[ServiceLogic] {

    override type State = Nothing

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): MissingParamHandleDynamicComponent$.ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
      NextParameters(Parameter[String]("param1") :: Nil)
    }

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): ServiceLogic = ???

    override def nodeDependencies: List[NodeDependency] = List.empty

  }

  object GenericParametersTransformer extends CustomStreamTransformer with GenericParameters[Null] {

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(String, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      dependencies.collectFirst { case OutputVariableNameValue(name) => name } match {
        case Some(name) =>
          finalResult(context, rest, name)
        case None =>
          FinalResults(context, errors = List(CustomNodeError("Output not defined", None)))
      }
    }

    override def nodeDependencies: List[NodeDependency] =
      List(OutputVariableNameDependency, TypedNodeDependency[MetaData], TypedNodeDependency[ComponentUseCase])

  }

  object GenericParametersTransformerUsingParameterValidator
      extends CustomStreamTransformer
      with SingleInputDynamicComponent[Validated[Unit, Int]] {
    override type State = Validated[Unit, Int]

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): GenericParametersTransformerUsingParameterValidator.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(
          List(
            Parameter("paramWithFixedValues", Typed[Int]).copy(editor =
              Some(FixedValuesParameterEditor(List(FixedExpressionValue("1", "One"), FixedExpressionValue("2", "Two"))))
            )
          )
        )
      case TransformationStep(
            ("paramWithFixedValues", DefinedEagerParameter(paramWithFixedValues: Int, _)) :: Nil,
            _
          ) =>
        FinalResults(context, state = Some(Valid(paramWithFixedValues)))
      case TransformationStep(("paramWithFixedValues", FailedToDefineParameter) :: Nil, _) =>
        FinalResults(context, state = Some(Invalid(())))
    }

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): Validated[Unit, Int] = finalState.get

    override def nodeDependencies: List[NodeDependency] = List.empty
  }

  class GenericParametersSource extends SourceFactory with GenericParameters[Source] with UnboundedStreamComponent {

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(String, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      finalResult(context, rest, "otherNameThanInput")
    }

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[List[String]]
    ): Source = {

      new Source with SourceTestSupport[String] with TestDataGenerator {

        override def testRecordParser: TestRecordParser[String] = (testRecord: TestRecord) =>
          CirceUtil.decodeJsonUnsafe[String](testRecord.json)

        override def generateTestData(size: Int): TestData = TestData((for {
          number <- 1 to size
          record = TestRecord(Json.fromString(s"record $number"), timestamp = Some(number))
        } yield record).toList)
      }
    }

  }

  class GenericParametersSourceNoTestSupport extends GenericParametersSource with UnboundedStreamComponent {

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[List[String]]
    ): Source = {
      new Source {
        // no override
      }
    }

  }

  class GenericParametersSourceNoGenerate extends GenericParametersSource with UnboundedStreamComponent {

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[List[String]]
    ): Source = {
      new Source with SourceTestSupport[String] {
        override def testRecordParser: TestRecordParser[String] = (testRecord: TestRecord) =>
          CirceUtil.decodeJsonUnsafe[String](testRecord.json)
      }
    }

  }

  class SourceWithTestParameters extends GenericParametersSource with UnboundedStreamComponent {

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[List[String]]
    ): Source = {
      new Source with SourceTestSupport[String] with TestWithParametersSupport[String] {
        override def testRecordParser: TestRecordParser[String] = (testRecord: TestRecord) =>
          CirceUtil.decodeJsonUnsafe[String](testRecord.json)

        override def testParametersDefinition: List[Parameter] = Nil

        override def parametersToTestData(params: Map[String, AnyRef]): String = ""
      }
    }

  }

  object GenericParametersSink extends SinkFactory with GenericParameters[Sink] {

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(String, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      FinalResults(context)
    }

  }

  object OptionalParametersSink extends SinkFactory with GenericParameters[Sink] {

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): OptionalParametersSink.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(List(Parameter.optional[CharSequence]("optionalParameter")))
      case TransformationStep(("optionalParameter", _) :: Nil, None) =>
        outputParameters(context, dependencies, List())
    }

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(String, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      FinalResults(context)
    }

  }

  object GenericParametersProcessor extends EagerService with GenericParameters[ServiceLogic] {

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(String, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      FinalResults(context)
    }

  }

  case object SomeException extends Exception("Some exception")

  object GenericParametersThrowingException extends EagerService with GenericParameters[ServiceLogic] {

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(String, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      throw SomeException
    }

  }

  object GenericParametersEnricher extends EagerService with GenericParameters[ServiceLogic] {

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(String, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      dependencies.collectFirst { case OutputVariableNameValue(name) => name } match {
        case Some(name) =>
          finalResult(context, rest, name)
        case None =>
          FinalResults(context, errors = List(CustomNodeError("Output not defined", None)))
      }
    }

    override def nodeDependencies: List[NodeDependency] =
      List(OutputVariableNameDependency, TypedNodeDependency[MetaData], TypedNodeDependency[ComponentUseCase])
  }

  trait GenericParameters[T] extends SingleInputDynamicComponent[T] {

    override type State = List[String]

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): this.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(List(Parameter[String]("par1"), Parameter[Long]("lazyPar1").copy(isLazyParameter = true)))
      case TransformationStep(("par1", DefinedEagerParameter(value: String, _)) :: ("lazyPar1", _) :: Nil, None) =>
        val split = value.split(",").toList
        NextParameters(split.map(Parameter(_, Unknown)), state = Some(split))
      case TransformationStep(("par1", _) :: ("lazyPar1", _) :: rest, Some(names)) if rest.map(_._1) == names =>
        outputParameters(context, dependencies, rest)
    }

    override protected def fallbackFinalResult(
        step: TransformationStep,
        inputContext: ValidationContext,
        outputVariable: Option[String]
    )(implicit nodeId: NodeId): FinalResults = {
      val result = Typed.record(
        step.parameters.toMap.filterKeysNow(k => k != "par1" && k != "lazyPar1").map { case (k, v) =>
          k -> v.returnType
        }
      )
      prepareFinalResultWithOptionalVariable(inputContext, outputVariable.map(name => (name, result)), step.state)
    }

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(String, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults

    protected def finalResult(context: ValidationContext, rest: List[(String, BaseDefinedParameter)], name: String)(
        implicit nodeId: NodeId
    ): this.FinalResults = {
      val result = Typed.record(rest.map { case (k, v) => k -> v.returnType }.toMap)
      prepareFinalResultWithOptionalVariable(context, Some((name, result)), None)
    }

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): T = {
      null.asInstanceOf[T]
    }

    override def nodeDependencies: List[NodeDependency] =
      List(TypedNodeDependency[MetaData], TypedNodeDependency[ComponentUseCase])

  }

  object GenericParametersTransformerWithTwoStepsThatCanBeDoneInOneStep
      extends CustomStreamTransformer
      with SingleInputDynamicComponent[String] {
    override type State = String

    val defaultExtraParamValue = "extraParamValue"

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(
          List(
            Parameter("moreParams", Typed[Boolean]).copy(defaultValue = Some(Expression.spel("true")))
          )
        )
      case TransformationStep(("moreParams", DefinedEagerParameter(true, _)) :: Nil, _) =>
        NextParameters(
          List(
            Parameter("extraParam", Typed[String])
              .copy(defaultValue = Some(Expression.spel(s"'$defaultExtraParamValue'")))
          )
        )
      case TransformationStep(
            ("moreParams", _) :: ("extraParam", DefinedEagerParameter(extraParamValue: String, _)) :: Nil,
            _
          ) =>
        FinalResults(context, state = Some(extraParamValue))
    }

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): String = finalState.get

    override def nodeDependencies: List[NodeDependency] = List.empty
  }

  object DynamicParameterJoinTransformer extends CustomStreamTransformer with JoinDynamicComponent[AnyRef] {

    override type State = Nothing

    // isLeft, key (branch) ==> rightValue
    override def contextTransformation(
        contexts: Map[String, ValidationContext],
        dependencies: List[NodeDependencyValue]
    )(implicit nodeId: NodeId): DynamicParameterJoinTransformer.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(List(Parameter[Boolean]("isLeft").copy(branchParam = true)))
      case TransformationStep(
            ("isLeft", DefinedEagerBranchParameter(byBranch: Map[String, Boolean] @unchecked, _)) :: Nil,
            _
          ) =>
        val error =
          if (byBranch.values.toList.sorted != List(false, true))
            List(CustomNodeError("Has to be exactly one left and right", Some("isLeft")))
          else Nil
        NextParameters(
          List(
            Parameter[Any]("rightValue").copy(
              isLazyParameter = true,
              additionalVariables =
                contexts(right(byBranch)).localVariables.mapValuesNow(AdditionalVariableProvidedInRuntime(_))
            )
          ),
          error
        )
      case TransformationStep(
            ("isLeft", DefinedEagerBranchParameter(byBranch: Map[String, Boolean] @unchecked, _)) :: (
              "rightValue",
              rightValue: DefinedSingleParameter
            ) :: Nil,
            _
          ) =>
        val out     = rightValue.returnType
        val outName = OutputVariableNameDependency.extract(dependencies)
        val leftCtx = contexts(left(byBranch))
        val context = leftCtx.withVariable(outName, out, paramName = None)
        FinalResults(context.getOrElse(leftCtx), context.fold(_.toList, _ => Nil))
    }

    private def left(byBranch: Map[String, Boolean]): String = byBranch.find(_._2).get._1

    private def right(byBranch: Map[String, Boolean]): String = byBranch.find(!_._2).get._1

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): AnyRef = null

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)
  }

  // this is to simulate wrong implementation of DynamicComponent
  object ParamsLoopNode extends CustomStreamTransformer with SingleInputDynamicComponent[String] {
    override type State = Nothing

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
      NextParameters(Nil)
    }

    override def createComponentLogic(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): String = ""

    override def nodeDependencies: List[NodeDependency] = List.empty
  }

  object OptionalParameterService extends Service {

    @MethodToInvoke
    def method(
        @ParamName("optionalParam")
        optionalParam: Option[String],
    ): Future[String] = ???

  }

}

package pl.touk.nussknacker.engine.compile

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import io.circe.Json
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ContextTransformation.DummyStreamTransformerImplementation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, FatalUnknownError}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.livedata.{DataRecord, DataRecords, LiveDataProvider}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.time.Instant
import javax.validation.constraints.{Min, NotBlank}
import scala.concurrent.Future
import scala.reflect.{classTag, ClassTag}

object validationHelpers {

  object SimpleStringSource extends SourceFactory with UnboundedStreamComponent {
    @MethodToInvoke(returnType = classOf[String])
    def create(): api.process.Source = new Source {}
  }

  object SimpleStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(
        @ParamName("stringVal")
        @AdditionalVariables(value =
          Array(new api.AdditionalVariable(name = "additionalVar1", clazz = classOf[String]))
        )
        stringVal: LazyParameter[String]
    ) = DummyStreamTransformerImplementation

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
        .notImplemented[DummyStreamTransformerImplementation]
    }

  }

  object ClearingContextStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute() = {
      ContextTransformation
        .definedBy(ctx => Valid(ctx.clearVariables))
        .notImplemented[DummyStreamTransformerImplementation]
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
          })
          context.withVariable(variableName, newType, paramName = None)
        }
        .notImplemented[DummyStreamTransformerImplementation]
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
          })
          Valid(ValidationContext(Map(variableName -> newType)))
        }
        .notImplemented[DummyStreamTransformerImplementation]
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
            Invalid(FatalUnknownError("Should be exact one main branch", Some(nodeId))).toValidatedNel
          } else {
            val mainBranchContext = mainBranches.head._2

            val newType = Typed.record(joinedBranches.map { case (branchId, _) =>
              branchId -> valueByBranchId(branchId).returnType
            })

            mainBranchContext.withVariable(variableName, newType, paramName = None)
          }
        }
        .notImplemented[DummyStreamTransformerImplementation]
    }

  }

  object UnionTransformerWithEagerBranchParam extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(
        @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[_]],
        @BranchParamName("label") @NotBlank labelByBranchId: Map[String, String],
        @OutputVariableName variableName: String
    ): JoinContextTransformation = {
      ContextTransformation.join
        .definedBy { contexts =>
          val newType = Typed.record(contexts.toSeq.map { case (branchId, _) =>
            branchId -> valueByBranchId(branchId).returnType
          })
          Valid(ValidationContext(Map(variableName -> newType)))
        }
        .notImplemented[DummyStreamTransformerImplementation]
    }

  }

  object NonEndingCustomNodeReturningTransformation extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): ContextTransformation = {
      ContextTransformation
        .definedBy(ctx => Valid(ctx.clearVariables))
        .notImplemented[DummyStreamTransformerImplementation]
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
    def execute(@ParamName("stringVal") stringVal: String) = DummyStreamTransformerImplementation

    override def canBeEnding: Boolean = true
  }

  object AddingVariableOptionalEndingStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String, @OutputVariableName variableName: String) =
      DummyStreamTransformerImplementation

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
        .notImplemented[DummyStreamTransformerImplementation]
    }

  }

  object MissingParamHandleDynamicComponent$ extends EagerService with SingleInputDynamicComponent {

    override type Implementation = ServiceInvoker

    override type State = Nothing

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): MissingParamHandleDynamicComponent$.ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
      NextParameters(Parameter[String](ParameterName("param1")) :: Nil)
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): ServiceInvoker = ???

    override def nodeDependencies: List[NodeDependency] = List.empty

  }

  object GenericParametersTransformer extends CustomStreamTransformer with GenericParameters {

    override type Implementation = DummyStreamTransformerImplementation

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(ParameterName, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): TransformationStepResult = {
      dependencies.collectFirst { case OutputVariableNameValue(name) => name } match {
        case Some(name) =>
          finalResult(context, rest, name)
        case None =>
          FinalResults(context, errors = List(CustomNodeError("Output not defined", None)))
      }
    }

    override def nodeDependencies: List[NodeDependency] =
      List(OutputVariableNameDependency, TypedNodeDependency[MetaData], TypedNodeDependency[ComponentUseContext])

    override protected def classTagT: ClassTag[DummyStreamTransformerImplementation] =
      classTag[DummyStreamTransformerImplementation]

  }

  object GenericParametersTransformerUsingParameterValidator
      extends CustomStreamTransformer
      with SingleInputDynamicComponent {

    override type Implementation = Validated[Unit, Int]

    override type State = Validated[Unit, Int]

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): GenericParametersTransformerUsingParameterValidator.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(
          List(
            Parameter(ParameterName("paramWithFixedValues"), Typed[Int]).copy(editors =
              List(FixedValuesParameterEditor(List(FixedExpressionValue("1", "One"), FixedExpressionValue("2", "Two"))))
            )
          )
        )
      case TransformationStep(
            (ParameterName("paramWithFixedValues"), DefinedEagerParameter(paramWithFixedValues: Int, _)) :: Nil,
            _
          ) =>
        FinalResults(context, state = Some(Valid(paramWithFixedValues)))
      case TransformationStep((ParameterName("paramWithFixedValues"), FailedToDefineParameter(_)) :: Nil, _) =>
        FinalResults(context, state = Some(Invalid(())))
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): Validated[Unit, Int] = finalState.get

    override def nodeDependencies: List[NodeDependency] = List.empty
  }

  object EagerValueValidatingTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("value") @Min(value = 5) value: java.lang.Long): ContextTransformation =
      ContextTransformation
        .definedBy(Valid(_))
        .notImplemented[DummyStreamTransformerImplementation]

  }

  object LazyValueValidatingTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("value") @Min(value = 5) value: LazyParameter[java.lang.Long]): ContextTransformation =
      ContextTransformation
        .definedBy(Valid(_))
        .notImplemented[DummyStreamTransformerImplementation]

  }

  object OptionalEagerValueValidatingTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("value") @Min(value = 5) value: Option[java.lang.Long]): ContextTransformation =
      ContextTransformation
        .definedBy(Valid(_))
        .notImplemented[DummyStreamTransformerImplementation]

  }

  object JavaOptionalEagerValueValidatingTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("value") @Min(value = 5) value: java.util.Optional[java.lang.Long]): ContextTransformation =
      ContextTransformation
        .definedBy(Valid(_))
        .notImplemented[DummyStreamTransformerImplementation]

  }

  class GenericParametersSource extends SourceFactory with GenericParameters with UnboundedStreamComponent {

    override type Implementation = Source

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(ParameterName, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): TransformationStepResult = {
      finalResult(context, rest, "otherNameThanInput")
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[List[String]]
    ): Source = {

      new Source with SourceTestSupport[ProcessingType] with TestDataGenerator with LiveDataProvider {

        override def testRecordParser: TestRecordParser[String] = (testRecords: List[TestRecord]) =>
          testRecords.map { testRecord =>
            CirceUtil.decodeJsonUnsafe[String](testRecord.json)
          }

        override def generateTestData(maxNumberOfRecords: Int): TestData = TestData((for {
          number <- 1 to maxNumberOfRecords
          record = TestRecord(Json.fromString(s"record $number"), timestamp = Some(number))
        } yield record).toList)

        override def fetchLiveData(maxNumberOfRecords: Int): DataRecords = DataRecords((for {
          number <- 1 to maxNumberOfRecords
          record = DataRecord(Map("input" -> s"record $number"), upstreamTimestamp = Some(Instant.ofEpochMilli(number)))
        } yield record).toList)
      }
    }

    override protected def classTagT: ClassTag[Source] = classTag[Source]

  }

  class GenericParametersSourceNoTestSupport extends GenericParametersSource with UnboundedStreamComponent {

    override def implementation(
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

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[List[String]]
    ): Source = {
      new Source with SourceTestSupport[String] {
        override def testRecordParser: TestRecordParser[String] = (testRecords: List[TestRecord]) =>
          testRecords.map { testRecord =>
            CirceUtil.decodeJsonUnsafe[String](testRecord.json)
          }
      }
    }

  }

  class SourceWithTestParameters extends GenericParametersSource with UnboundedStreamComponent {

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[List[String]]
    ): Source = {
      new Source with SourceTestSupport[String] with TestWithParametersSupport[String] {
        override def testRecordParser: TestRecordParser[String] = (testRecords: List[TestRecord]) =>
          testRecords.map { testRecord =>
            CirceUtil.decodeJsonUnsafe[String](testRecord.json)
          }

        override def testParametersDefinition: List[Parameter] = params.nameToRawValueMap.map { case (k, v) =>
          Parameter(k, Typed.fromInstance(v))
        }.toList

        override def parametersToTestData(params: Map[ParameterName, AnyRef]): String = ""
      }
    }

  }

  object GenericParametersSink extends SinkFactory with GenericParameters {

    override type Implementation = Sink

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(ParameterName, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      FinalResults(context)
    }

    override protected def classTagT: ClassTag[Sink] = classTag[Sink]

  }

  object OptionalParametersSink extends SinkFactory with GenericParameters {

    override type Implementation = Sink

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): OptionalParametersSink.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(List(Parameter.optional[CharSequence](ParameterName("optionalParameter"))))
      case TransformationStep((ParameterName("optionalParameter"), _) :: Nil, None) =>
        outputParameters(context, dependencies, List())
    }

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(ParameterName, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      FinalResults(context)
    }

    override protected def classTagT: ClassTag[Sink] = classTag[Sink]

  }

  object GenericParametersProcessor extends EagerService with GenericParameters {

    override type Implementation = ServiceInvoker

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(ParameterName, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      FinalResults(context)
    }

    override protected def classTagT: ClassTag[ServiceInvoker] = classTag[ServiceInvoker]

  }

  case object SomeException extends Exception("Some exception")

  object GenericParametersThrowingException extends EagerService with GenericParameters {

    override type Implementation = ServiceInvoker

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(ParameterName, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      throw SomeException
    }

    override protected def classTagT: ClassTag[ServiceInvoker] = classTag[ServiceInvoker]

  }

  object GenericParametersEnricher extends EagerService with GenericParameters {

    override type Implementation = ServiceInvoker

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(ParameterName, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): TransformationStepResult = {
      dependencies.collectFirst { case OutputVariableNameValue(name) => name } match {
        case Some(name) =>
          finalResult(context, rest, name)
        case None =>
          FinalResults(context, errors = List(CustomNodeError("Output not defined", None)))
      }
    }

    override def nodeDependencies: List[NodeDependency] =
      List(OutputVariableNameDependency, TypedNodeDependency[MetaData], TypedNodeDependency[ComponentUseContext])

    override protected def classTagT: ClassTag[ServiceInvoker] = classTag[ServiceInvoker]

  }

  trait GenericParameters extends SingleInputDynamicComponent {

    protected def classTagT: ClassTag[Implementation]

    override type State = List[String]

    private val par1ParamName     = ParameterName("par1")
    private val lazyPar1ParamName = ParameterName("lazyPar1")

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): this.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(
          List(
            Parameter[String](par1ParamName),
            Parameter[Long](lazyPar1ParamName).copy(isLazyParameter = true)
          )
        )
      case TransformationStep(
            (`par1ParamName`, DefinedEagerParameter(value: String, _)) :: (`lazyPar1ParamName`, _) :: Nil,
            None
          ) =>
        val split = value.split(",").toList
        NextParameters(split.map(v => Parameter(ParameterName(v), Unknown)), state = Some(split))
      case TransformationStep((`par1ParamName`, _) :: (`lazyPar1ParamName`, _) :: rest, Some(names))
          if rest.map(_._1.value) == names =>
        outputParameters(context, dependencies, rest)
    }

    override protected def fallbackFinalResult(
        step: TransformationStep,
        inputContext: ValidationContext,
        outputVariable: Option[String]
    )(implicit nodeId: NodeId): TransformationStepResult = {
      val result = Typed.record(
        step.parameters.toMap
          .filterKeysNow(k => k != par1ParamName && k != lazyPar1ParamName)
          .map { case (k, v) => k.value -> v.returnType }
      )
      prepareFinalResultWithOptionalVariable(inputContext, outputVariable.map(name => (name, result)), step.state)
    }

    protected def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(ParameterName, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): TransformationStepResult

    protected def finalResult(
        context: ValidationContext,
        rest: List[(ParameterName, BaseDefinedParameter)],
        name: String
    )(
        implicit nodeId: NodeId
    ): TransformationStepResult = {
      val result = Typed.record(rest.map { case (k, v) => k.value -> v.returnType })
      prepareFinalResultWithOptionalVariable(context, Some((name, result)), None)
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): Implementation = {
      implicit val implicitClassTagT: ClassTag[Implementation] = classTagT
      ReflectUtils.createADummyInstanceOf[Implementation]
    }

    override def nodeDependencies: List[NodeDependency] =
      List(TypedNodeDependency[MetaData], TypedNodeDependency[ComponentUseContext])

  }

  object GenericParametersTransformerWithTwoStepsThatCanBeDoneInOneStep
      extends CustomStreamTransformer
      with SingleInputDynamicComponent {

    override type Implementation = String

    override type State = String

    val defaultExtraParamValue = "extraParamValue"

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(
          List(
            Parameter(ParameterName("moreParams"), Typed[Boolean]).copy(defaultValue = Some(Expression.spel("true")))
          )
        )
      case TransformationStep((ParameterName("moreParams"), DefinedEagerParameter(true, _)) :: Nil, _) =>
        NextParameters(
          List(
            Parameter(ParameterName("extraParam"), Typed[String])
              .copy(defaultValue = Some(Expression.spel(s"'$defaultExtraParamValue'")))
          )
        )
      case TransformationStep(
            (ParameterName("moreParams"), _) :: (
              ParameterName("extraParam"),
              DefinedEagerParameter(extraParamValue: String, _)
            ) :: Nil,
            _
          ) =>
        FinalResults(context, state = Some(extraParamValue))
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): String = finalState.get

    override def nodeDependencies: List[NodeDependency] = List.empty
  }

  object DynamicParameterJoinTransformer extends CustomStreamTransformer with JoinDynamicComponent {

    override type Implementation = AnyRef

    override type State = Nothing

    // isLeft, key (branch) ==> rightValue
    override def contextTransformation(
        contexts: Map[String, ValidationContext],
        dependencies: List[NodeDependencyValue]
    )(implicit nodeId: NodeId): DynamicParameterJoinTransformer.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(List(Parameter[Boolean](ParameterName("isLeft")).copy(branchParam = true)))
      case TransformationStep(
            (ParameterName("isLeft"), DefinedEagerBranchParameter(byBranch: Map[String, Boolean] @unchecked, _)) :: Nil,
            _
          ) =>
        val error =
          if (byBranch.values.toList.sorted != List(false, true))
            List(CustomNodeError("Has to be exactly one left and right", Some(ParameterName("isLeft"))))
          else Nil
        NextParameters(
          List(
            Parameter[Any](ParameterName("rightValue")).copy(
              isLazyParameter = true,
              additionalVariables =
                contexts(right(byBranch)).localVariables.mapValuesNow(AdditionalVariableProvidedInRuntime(_))
            )
          ),
          error
        )
      case TransformationStep(
            (ParameterName("isLeft"), DefinedEagerBranchParameter(byBranch: Map[String, Boolean] @unchecked, _)) ::
            (ParameterName("rightValue"), rightValue: DefinedSingleParameter) :: Nil,
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

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): AnyRef = null

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)
  }

  // this is to simulate wrong implementation of DynamicComponent
  object ParamsLoopNode extends CustomStreamTransformer with SingleInputDynamicComponent {

    override type Implementation = String

    override type State = Nothing

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
      NextParameters(Nil)
    }

    override def implementation(
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

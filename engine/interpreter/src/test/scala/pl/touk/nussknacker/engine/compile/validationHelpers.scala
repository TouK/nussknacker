package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.{AdditionalVariable, AdditionalVariables, BranchParamName, CustomStreamTransformer, LazyParameter, MetaData, MethodToInvoke, OutputVariableName, ParamName, Service}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, FatalUnknownError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, DefinedEagerBranchParameter, DefinedEagerParameter, DefinedSingleParameter, FailedToDefineParameter, JoinGenericNodeTransformation, NodeDependencyValue, OutputVariableNameValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory, Source, SourceFactory, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.TestInfoProvider

import scala.concurrent.Future

object validationHelpers {

  object SimpleStringSource extends SourceFactory[String] {
    override def clazz: Class[_] = classOf[String]

    @MethodToInvoke
    def create(): api.process.Source[String] = null
  }

  object SimpleStreamTransformer extends CustomStreamTransformer {
    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("stringVal")
                @AdditionalVariables(value = Array(new AdditionalVariable(name = "additionalVar1", clazz = classOf[String])))
                stringVal: String) = {}
  }

  object SimpleStringService extends Service {
    @MethodToInvoke
    def invoke(@ParamName("stringParam") param: String): Future[Unit] = ???
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
        .implementedBy(null)
    }

  }

  object ClearingContextStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute() = {
      ContextTransformation
        .definedBy(ctx => Valid(ctx.clearVariables))
        .implementedBy(null)
    }

  }

  object ProducingTupleTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("numberOfFields") numberOfFields: Int,
                @OutputVariableName variableName: String)
               (implicit nodeId: NodeId): ContextTransformation = {
      ContextTransformation
        .definedBy { context =>
          val newType = TypedObjectTypingResult((1 to numberOfFields).map { i =>
            s"field$i" -> Typed[String]
          }.toMap)
          context.withVariable(variableName, newType, paramName = None)
        }
        .implementedBy(null)
    }

  }

  object UnionTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[CharSequence]], // key is only for runtime purpose
                @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[_]],
                @OutputVariableName variableName: String): JoinContextTransformation = {
      ContextTransformation
        .join
        .definedBy { contexts =>
          val newType = TypedObjectTypingResult(contexts.toSeq.map {
            case (branchId, _) =>
              branchId -> valueByBranchId(branchId).returnType
          }.toMap)
          Valid(ValidationContext(Map(variableName -> newType)))
        }
        .implementedBy(null)
    }

  }

  object UnionTransformerWithMainBranch extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[_]], // key is only for runtime purpose
                @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[_]],
                @BranchParamName("mainBranch") mainBranch: Map[String, Boolean],
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation = {
      ContextTransformation
        .join
        .definedBy { contexts =>
          val (mainBranches, joinedBranches) = contexts.partition {
            case (branchId, _) => mainBranch(branchId)
          }
          if (mainBranches.size != 1) {
            Invalid(FatalUnknownError("Should be exact one main branch")).toValidatedNel
          } else {
            val mainBranchContext = mainBranches.head._2

            val newType = TypedObjectTypingResult(joinedBranches.toSeq.map {
              case (branchId, _) =>
                branchId -> valueByBranchId(branchId).returnType
            }.toMap)

            mainBranchContext.withVariable(variableName, newType, paramName = None)
          }
        }
        .implementedBy(null)
    }

  }

  object NonEndingCustomNodeReturningTransformation extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): ContextTransformation = {
      ContextTransformation
        .definedBy(ctx => Valid(ctx.clearVariables))
        .implementedBy(null)
    }

    override def canBeEnding: Boolean = false
  }

  object NonEndingCustomNodeReturningUnit extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): Unit = {
    }

    override def canBeEnding: Boolean = false
  }

  object OptionalEndingStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String): Unit = {
    }

    override def canBeEnding: Boolean = true
  }

  object AddingVariableOptionalEndingStreamTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("stringVal") stringVal: String,
                @OutputVariableName variableName: String): Unit = {
    }

    override def canBeEnding: Boolean = true
  }

  object DynamicNoBranchParameterJoinTransformer extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(implicit nodeId: NodeId): JoinContextTransformation = {
      ContextTransformation.join.definedBy(contexts => {
        contexts.values.toList.distinct match {
          case Nil => Invalid(CustomNodeError("At least one validation context needed", Option.empty)).toValidatedNel
          case one::Nil => Valid(one)
          case _ => Invalid(CustomNodeError("Validation contexts do not match", Option.empty)).toValidatedNel
        }
      }).implementedBy(null)
    }
  }

  object GenericParametersTransformer extends CustomStreamTransformer with GenericParameters[Null] {

    protected def outputParameters(context: ValidationContext, dependencies: List[NodeDependencyValue], rest: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): this.FinalResults = {
      dependencies.collectFirst { case OutputVariableNameValue(name) => name } match {
        case Some(name) =>
          finalResult(context, rest, name)
        case None =>
          FinalResults(context, errors = List(CustomNodeError("Output not defined", None)))
      }
    }
    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency, TypedNodeDependency(classOf[MetaData]))

  }

  object GenericParametersSource extends SourceFactory[String] with GenericParameters[Source[String]] {
    override def clazz: Class[_] = classOf[String]

    protected def outputParameters(context: ValidationContext, dependencies: List[NodeDependencyValue], rest: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): this.FinalResults = {
      finalResult(context, rest, "otherNameThanInput")
    }
    override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]))

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[List[String]]): Source[String] = {

      new Source[String] with TestDataGenerator {
        override def generateTestData(size: Int): Array[Byte] = Array(0)
      }
    }
  }

  object GenericParametersSink extends SinkFactory with GenericParameters[Sink] {
    protected def outputParameters(context: ValidationContext, dependencies: List[NodeDependencyValue], rest: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): this.FinalResults = {
      FinalResults(context)
    }
    override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]))

  }

  trait GenericParameters[T] extends SingleInputGenericNodeTransformation[T] {

    override type State = List[String]

    override def contextTransformation(context: ValidationContext,
                                       dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): this.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(initialParameters)
      case TransformationStep(("par1", DefinedEagerParameter(value: String, _))::("lazyPar1", _)::Nil, None) =>
        val split = value.split(",").toList
        NextParameters(split.map(Parameter(_, Unknown)), state = Some(split))
      case TransformationStep(("par1", FailedToDefineParameter)::("lazyPar1", _)::Nil, None) =>
        outputParameters(context, dependencies, Nil)
      case TransformationStep(("par1", _)::("lazyPar1", _)::rest, Some(names)) if rest.map(_._1) == names =>
        outputParameters(context, dependencies, rest)
    }

    protected def outputParameters(context: ValidationContext, dependencies: List[NodeDependencyValue], rest: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): this.FinalResults

    protected def finalResult(context: ValidationContext, rest: List[(String, BaseDefinedParameter)], name: String)(implicit nodeId: NodeId): this.FinalResults = {
      val result = TypedObjectTypingResult(rest.toMap.mapValues(_.returnType))
      context.withVariable(name, result, paramName = None).fold(
        errors => FinalResults(context, errors.toList),
        FinalResults(_))
    }

    override def initialParameters: List[Parameter] = List(
      Parameter[String]("par1"), Parameter[Long]("lazyPar1").copy(isLazyParameter = true)
    )

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): T = {
      null.asInstanceOf[T]
    }

  }

  object DynamicParameterJoinTransformer extends CustomStreamTransformer with JoinGenericNodeTransformation[AnyRef] {

    override type State = Nothing

    //isLeft, key (branch) ==> rightValue
    override def contextTransformation(contexts: Map[String, ValidationContext],
                                       dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): DynamicParameterJoinTransformer.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(initialParameters)
      case TransformationStep(("isLeft", DefinedEagerBranchParameter(byBranch: Map[String, Boolean]@unchecked, _)) ::Nil, _) =>
        val error = if (byBranch.values.toList.sorted != List(false, true)) List(CustomNodeError("Has to be exactly one left and right",
          Some("isLeft"))) else Nil
        NextParameters(
          List(Parameter[Any]("rightValue").copy(additionalVariables = contexts(right(byBranch)).localVariables)), error
        )
      case TransformationStep(("isLeft", DefinedEagerBranchParameter(byBranch: Map[String, Boolean]@unchecked, _)) :: ("rightValue", rightValue: DefinedSingleParameter) ::Nil, _)
        =>
        val out = rightValue.returnType
        val outName = OutputVariableNameDependency.extract(dependencies)
        val leftCtx = contexts(left(byBranch))
        val context = leftCtx.withVariable(outName, out, paramName = None)
        FinalResults(context.getOrElse(leftCtx), context.fold(_.toList, _ => Nil))
    }

    private def left(byBranch: Map[String, Boolean]): String = byBranch.find(_._2).get._1

    private def right(byBranch: Map[String, Boolean]): String = byBranch.find(!_._2).get._1

    override def initialParameters: List[Parameter] = List(Parameter[Boolean]("isLeft").copy(branchParam = true))

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): AnyRef = null

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)
  }


}

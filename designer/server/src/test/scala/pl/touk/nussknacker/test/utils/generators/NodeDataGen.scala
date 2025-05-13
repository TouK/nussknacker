package pl.touk.nussknacker.test.utils.generators

import org.scalacheck.{Arbitrary, Gen}
import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field

class NodeDataGen private {
  private lazy val booleanGen = Arbitrary.arbitrary[Boolean]

  private lazy val stringGen = Gen.oneOf("foo", "bar", "cat", "dog", "cactus")

  private lazy val mapGen = for {
    key   <- stringGen
    value <- stringGen
  } yield (key, value)

  lazy val expressionGen: Gen[Expression] = for {
    language   <- Gen.oneOf(Language.Spel, Language.SpelTemplate)
    expression <- stringGen
  } yield Expression(language, expression)

  lazy val layoutDataGen: Gen[LayoutData] = for {
    x <- Gen.long
    y <- Gen.long
  } yield LayoutData(x, y)

  lazy val userDefinedNodeFieldsGen: Gen[UserDefinedAdditionalNodeFields] = for {
    description <- Gen.option(stringGen)
    layoutData  <- Gen.option(layoutDataGen)
  } yield UserDefinedAdditionalNodeFields(description, layoutData)

  lazy val fieldGen: Gen[Field] = for {
    name       <- stringGen
    expression <- expressionGen
  } yield Field(name, expression)

  lazy val fieldListGen: Gen[List[Field]] = Gen.listOfN(5, fieldGen)

  lazy val branchEndDefinitionGen: Gen[BranchEndDefinition] = for {
    id     <- stringGen
    joinId <- stringGen
  } yield BranchEndDefinition(id, joinId)

  lazy val parameterGen: Gen[Parameter] = for {
    parameterName <- stringGen
    expression    <- expressionGen
  } yield Parameter(ParameterName(parameterName), expression)

  lazy val parametersListGen: Gen[List[Parameter]] = Gen.listOfN(5, parameterGen)

  lazy val serviceRefGen: Gen[ServiceRef] = for {
    id         <- stringGen
    parameters <- parametersListGen
  } yield ServiceRef(id, parameters)

  lazy val fragmentRefGen: Gen[FragmentRef] = for {
    id                  <- stringGen
    parameters          <- parametersListGen
    outputVariableNames <- Gen.mapOfN(5, mapGen)
  } yield FragmentRef(id, parameters, outputVariableNames)

  lazy val fragmentParamGen: Gen[FragmentParameter] = for {
    name <- stringGen
    typ  <- stringGen
  } yield FragmentParameter(ParameterName(name), FragmentClazzRef(typ))

  lazy val fragmentParametersGen: Gen[List[FragmentParameter]] = Gen.listOfN(5, fragmentParamGen)

  lazy val branchParamGen: Gen[BranchParameters] = for {
    branchId   <- stringGen
    parameters <- parametersListGen
  } yield BranchParameters(branchId, parameters)

  lazy val branchParametersGen: Gen[List[BranchParameters]] = Gen.listOfN(5, branchParamGen)

  lazy val sourceRefGen: Gen[SourceRef] = for {
    typ        <- stringGen
    parameters <- parametersListGen
  } yield SourceRef(typ, parameters)

  private lazy val variableGen: Gen[Variable] = for {
    id               <- stringGen
    varName          <- stringGen
    expression       <- expressionGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield Variable(id, varName, expression, additionalFields)

  private lazy val variableBuilderGen: Gen[VariableBuilder] = for {
    id               <- stringGen
    varName          <- stringGen
    fields           <- fieldListGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield VariableBuilder(id, varName, fields, additionalFields)

  private lazy val branchEndDataGen: Gen[BranchEndData] = for {
    definition <- branchEndDefinitionGen
  } yield BranchEndData(definition)

  private lazy val customNodeGen: Gen[CustomNode] = for {
    id               <- stringGen
    outputVar        <- Gen.option(stringGen)
    nodeType         <- stringGen
    parameters       <- parametersListGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield CustomNode(id, outputVar, nodeType, parameters, additionalFields)

  private lazy val enricherGen: Gen[Enricher] = for {
    id               <- stringGen
    service          <- serviceRefGen
    output           <- stringGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield Enricher(id, service, output, additionalFields)

  private lazy val filterGen: Gen[Filter] = for {
    id               <- stringGen
    expression       <- expressionGen
    isDisabled       <- Gen.option(booleanGen)
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield Filter(id, expression, isDisabled, additionalFields)

  private lazy val fragmentInputGen: Gen[FragmentInput] = for {
    id               <- stringGen
    ref              <- fragmentRefGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
    isDisabled       <- Gen.option(booleanGen)
    fragmentParams   <- Gen.option(fragmentParametersGen)
  } yield FragmentInput(id, ref, additionalFields, isDisabled, fragmentParams)

  private lazy val fragmentInputDefinitionGen: Gen[FragmentInputDefinition] = for {
    id               <- stringGen
    parameters       <- fragmentParametersGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield FragmentInputDefinition(id, parameters, additionalFields)

  private lazy val fragmentOutputDefinitionGen: Gen[FragmentOutputDefinition] = for {
    id               <- stringGen
    outputName       <- stringGen
    fields           <- fieldListGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield FragmentOutputDefinition(id, outputName, fields, additionalFields)

  private lazy val joinGen: Gen[Join] = for {
    id               <- stringGen
    outputVar        <- Gen.option(stringGen)
    nodeType         <- stringGen
    parameters       <- parametersListGen
    branchParameters <- branchParametersGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield Join(id, outputVar, nodeType, parameters, branchParameters, additionalFields)

  private lazy val processorGen: Gen[Processor] = for {
    id               <- stringGen
    service          <- serviceRefGen
    isDisabled       <- Gen.option(booleanGen)
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield Processor(id, service, isDisabled, additionalFields)

  private lazy val sourceGen: Gen[Source] = for {
    id               <- stringGen
    ref              <- sourceRefGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield Source(id, ref, additionalFields)

  private lazy val splitGen: Gen[Split] = for {
    id               <- stringGen
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield Split(id, additionalFields)

  private lazy val switchGen: Gen[Switch] = for {
    id               <- stringGen
    expression       <- Gen.option(expressionGen)
    exprVal          <- Gen.option(stringGen)
    additionalFields <- Gen.option(userDefinedNodeFieldsGen)
  } yield Switch(id, expression, exprVal, additionalFields)

  private def genFromListOfGens[T](list: List[Gen[T]]): Gen[T] = {
    Gen.choose(0, list.size - 1).flatMap(list(_))
  }

  lazy val nodeDataGen: Gen[NodeData] =
    Gen.lzy {
      genFromListOfGens(
        List(
          variableGen,
          variableBuilderGen,
          branchEndDataGen,
          customNodeGen,
          enricherGen,
          filterGen,
          fragmentInputGen,
          fragmentInputDefinitionGen,
          fragmentOutputDefinitionGen,
          joinGen,
          processorGen,
          sourceGen,
          splitGen,
          switchGen
        )
      )
    }

}

object NodeDataGen {
  def nodeDataGen: Gen[NodeData] = new NodeDataGen().nodeDataGen
}

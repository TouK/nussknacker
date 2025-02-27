package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.{LazyParameter, Params}
import pl.touk.nussknacker.engine.api.definition.ParameterDeclarationBuilder.ParamType
import pl.touk.nussknacker.engine.api.definition.ParameterDeclarationBuilder.ParamType._
import pl.touk.nussknacker.engine.api.definition.ParameterExtractor._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.util.NotNothing

import scala.reflect.runtime.universe._

/**
 * It is helper class that holds runtime value type next to definition of parameter.
 * It reduce boilerplate defining `DynamicComponent` and reduce risk that definition of parameter
 * will desynchronize with implementation code using values
 */
object ParameterDeclaration {

  // TODO: At the moment there is no way to declare parameter that depends on value of other parameter. The existence
  //       of the parameter name in the Params' map depends on the value of the other parameter. Developer should be
  //       careful and check the value before extracting the depending parameter value from Params instance.
  //       We will improve it in the future by introducing depending parameters in the code domain.
  def mandatory[T: TypeTag: NotNothing](name: ParameterName): ParameterDeclarationBuilder[Mandatory[T]] =
    new ParameterDeclarationBuilder(Mandatory[T](name))

  def branchMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[BranchMandatory[T]] =
    new ParameterDeclarationBuilder(BranchMandatory[T](name))

  def lazyMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[LazyMandatory[T]] =
    new ParameterDeclarationBuilder(LazyMandatory[T](name))

  def branchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[BranchLazyMandatory[T]] =
    new ParameterDeclarationBuilder(BranchLazyMandatory[T](name))

  def optional[T: TypeTag: NotNothing](name: ParameterName): ParameterDeclarationBuilder[Optional[T]] = {
    new ParameterDeclarationBuilder(Optional[T](name))
  }

  def branchOptional[T: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[BranchOptional[T]] =
    new ParameterDeclarationBuilder(BranchOptional[T](name))

  def lazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[LazyOptional[T]] =
    new ParameterDeclarationBuilder(LazyOptional[T](name))

  def branchLazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName
  ): ParameterDeclarationBuilder[BranchLazyOptional[T]] =
    new ParameterDeclarationBuilder(BranchLazyOptional[T](name))

}

sealed trait ParameterCreator[DEPENDENCY] extends Serializable {
  def createParameter: DEPENDENCY => Parameter
}

sealed trait ParameterCreatorWithNoDependency extends Serializable {
  def createParameter(): Parameter
}

sealed abstract class ParameterExtractor[PARAMETER_VALUE_TYPE] extends Serializable {

  def parameterName: ParameterName

  def extractValue(params: Params): Option[PARAMETER_VALUE_TYPE]

  def extractValueUnsafe(params: Params): PARAMETER_VALUE_TYPE = {
    extractValue(params)
      .getOrElse(
        throw new IllegalArgumentException(s"Parameter [${parameterName.value}] doesn't expect to be null!")
      )
  }

  private[definition] def createBase: Parameter
}

object ParameterExtractor {

  final class MandatoryParamExtractor[PARAMETER_VALUE_TYPE: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[PARAMETER_VALUE_TYPE] {

    override def extractValue(params: Params): Option[PARAMETER_VALUE_TYPE] =
      params.extract[PARAMETER_VALUE_TYPE](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[PARAMETER_VALUE_TYPE](parameterName)
  }

  final class MandatoryBranchParamExtractor[PARAMETER_VALUE_TYPE: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Map[String, PARAMETER_VALUE_TYPE]] {

    override def extractValue(params: Params): Option[Map[String, PARAMETER_VALUE_TYPE]] =
      params.extract[Map[String, PARAMETER_VALUE_TYPE]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[PARAMETER_VALUE_TYPE](parameterName)
        .copy(branchParam = true)

  }

  final class MandatoryLazyParamExtractor[PARAMETER_VALUE_TYPE <: AnyRef: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[LazyParameter[PARAMETER_VALUE_TYPE]] {

    override def extractValue(params: Params): Option[LazyParameter[PARAMETER_VALUE_TYPE]] =
      params.extract[LazyParameter[PARAMETER_VALUE_TYPE]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[PARAMETER_VALUE_TYPE](parameterName)
        .copy(isLazyParameter = true)

  }

  final class MandatoryBranchLazyParamExtractor[PARAMETER_VALUE_TYPE <: AnyRef: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]] {

    override def extractValue(params: Params): Option[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]] =
      params.extract[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter[PARAMETER_VALUE_TYPE](parameterName)
        .copy(isLazyParameter = true, branchParam = true)

  }

  final class OptionalParamExtractor[PARAMETER_VALUE_TYPE: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[PARAMETER_VALUE_TYPE] {

    override def extractValue(params: Params): Option[PARAMETER_VALUE_TYPE] = {
      params.extract[PARAMETER_VALUE_TYPE](parameterName)
    }

    override private[definition] def createBase: Parameter =
      Parameter.optional[PARAMETER_VALUE_TYPE](parameterName)
  }

  final class OptionalLazyParamExtractor[PARAMETER_VALUE_TYPE <: AnyRef: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[LazyParameter[PARAMETER_VALUE_TYPE]] {

    override def extractValue(params: Params): Option[LazyParameter[PARAMETER_VALUE_TYPE]] =
      params.extract[LazyParameter[PARAMETER_VALUE_TYPE]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter
        .optional[PARAMETER_VALUE_TYPE](parameterName)
        .copy(isLazyParameter = true)

  }

  final class OptionalBranchParamExtractor[PARAMETER_VALUE_TYPE: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Map[String, PARAMETER_VALUE_TYPE]] {

    override def extractValue(params: Params): Option[Map[String, PARAMETER_VALUE_TYPE]] =
      params.extract[Map[String, PARAMETER_VALUE_TYPE]](parameterName)

    override private[definition] def createBase: Parameter =
      Parameter
        .optional[PARAMETER_VALUE_TYPE](parameterName)
        .copy(branchParam = true)

  }

  final class OptionalBranchLazyParamExtractor[PARAMETER_VALUE_TYPE <: AnyRef: TypeTag] private[definition] (
      override val parameterName: ParameterName,
  ) extends ParameterExtractor[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]] {

    override def extractValue(params: Params): Option[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]] =
      params.extract[Map[String, LazyParameter[PARAMETER_VALUE_TYPE]]](parameterName)

    override private[definition] def createBase: Parameter = {
      Parameter
        .optional[PARAMETER_VALUE_TYPE](parameterName)
        .copy(isLazyParameter = true, branchParam = true)
    }

  }

}

class ParameterDeclarationBuilder[T <: ParamType] private[definition] (paramType: T) {

  def withCreator(
      modify: Parameter => Parameter = identity
  ): ParameterExtractor[T#EXTRACTED_VALUE_TYPE] with ParameterCreatorWithNoDependency = {
    val underlying: ParameterExtractor[T#EXTRACTED_VALUE_TYPE] =
      paramType.extractor.asInstanceOf[ParameterExtractor[T#EXTRACTED_VALUE_TYPE]]
    new ParameterExtractor[T#EXTRACTED_VALUE_TYPE] with ParameterCreatorWithNoDependency {
      override def parameterName: ParameterName                                 = underlying.parameterName
      override def extractValue(params: Params): Option[T#EXTRACTED_VALUE_TYPE] = underlying.extractValue(params)
      override def createParameter(): Parameter                                 = modify(underlying.createBase)
      override private[definition] def createBase: Parameter                    = underlying.createBase
    }
  }

  def withAdvancedCreator[DEPENDENCY](
      create: DEPENDENCY => Parameter => Parameter
  ): ParameterExtractor[T#EXTRACTED_VALUE_TYPE] with ParameterCreator[DEPENDENCY] = {
    val underlying: ParameterExtractor[T#EXTRACTED_VALUE_TYPE] =
      paramType.extractor.asInstanceOf[ParameterExtractor[T#EXTRACTED_VALUE_TYPE]]
    new ParameterExtractor[T#EXTRACTED_VALUE_TYPE] with ParameterCreator[DEPENDENCY] {
      override def parameterName: ParameterName                                 = underlying.parameterName
      override def extractValue(params: Params): Option[T#EXTRACTED_VALUE_TYPE] = underlying.extractValue(params)
      override def createParameter: DEPENDENCY => Parameter  = dependency => create(dependency)(underlying.createBase)
      override private[definition] def createBase: Parameter = underlying.createBase
    }
  }

}

object ParameterDeclarationBuilder {

  sealed abstract class ParamType {
    type EXTRACTED_VALUE_TYPE

    def extractor: ParameterExtractor[EXTRACTED_VALUE_TYPE]
  }

  object ParamType {

    final case class Mandatory[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = T
      override lazy val extractor: ParameterExtractor[EXTRACTED_VALUE_TYPE] =
        new MandatoryParamExtractor[T](parameterName)
    }

    final case class LazyMandatory[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = LazyParameter[T]
      override lazy val extractor: ParameterExtractor[EXTRACTED_VALUE_TYPE] =
        new MandatoryLazyParamExtractor[T](parameterName)
    }

    final case class BranchMandatory[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Map[String, T]

      override lazy val extractor: ParameterExtractor[Map[String, T]] = new MandatoryBranchParamExtractor(
        parameterName
      )

    }

    final case class BranchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName)
        extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Map[String, LazyParameter[T]]
      override lazy val extractor: ParameterExtractor[Map[String, LazyParameter[T]]] =
        new MandatoryBranchLazyParamExtractor[T](parameterName)
    }

    final case class Optional[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = T
      override lazy val extractor: ParameterExtractor[T] = new OptionalParamExtractor[T](parameterName)
    }

    final case class LazyOptional[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = LazyParameter[T]
      override lazy val extractor: ParameterExtractor[LazyParameter[T]] =
        new OptionalLazyParamExtractor[T](parameterName)
    }

    final case class BranchOptional[T: TypeTag: NotNothing](parameterName: ParameterName) extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Map[String, T]

      override lazy val extractor: ParameterExtractor[Map[String, T]] =
        new OptionalBranchParamExtractor(parameterName)

    }

    final case class BranchLazyOptional[T <: AnyRef: TypeTag: NotNothing](parameterName: ParameterName)
        extends ParamType {
      override type EXTRACTED_VALUE_TYPE = Map[String, LazyParameter[T]]
      override lazy val extractor: ParameterExtractor[Map[String, LazyParameter[T]]] =
        new OptionalBranchLazyParamExtractor[T](parameterName)
    }

  }

}

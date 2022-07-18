//types from pl.touk.nussknacker.ui.definition

interface TypingResultBase {
  type: string,
  display: string,
  refClazzName: string,
}

interface TypedClass {
  refClazzName: string,
  params: Array<TypingResult>,
}

export interface TypedObjectTypingResult extends TypingResultBase, TypedClass {
  fields: Record<string, TypingResult>,
}

interface TypedDict extends TypingResultBase {
  id: string,
  valueType: SingleTypingResult,
}

type TypedTaggedValue = (TypedObjectTypingResult | TypedDict | TypedClass) & {
  tag: string,
}

type SingleTypingResult = TypingResultBase &
  (TypedObjectTypingResult | TypedDict | TypedClass | TypedTaggedValue)

interface UnknownTyping extends TypingResultBase {
  params: Array<TypingResult>,
}

interface UnionTyping extends TypingResultBase {
  union: Array<SingleTypingResult>,
}

export type TypingResult = UnknownTyping | SingleTypingResult | UnionTyping

export interface UIParameter {
  name: string,
  typ: TypingResult,
  editor: $TodoType,
  validators: $TodoType,
  defaultValue: string,
  additionalVariables: Record<string, TypingResult>,
  variablesToHide: Array<string>,
  branchParam: boolean,
}

//types from pl.touk.nussknacker.ui.definition

export type UIObjectDefinition = {
    parameters: Array<UIParameter>,
    returnType?: TypingResult,
    categories: Array<string>,
    nodeConfig: SingleNodeConfig,
}

interface TypingResultBase {
    type: string,
    display: string, 
}

type TypedClass = {
    refClazzName: string,
    params: Array<TypingResult>, 
}

type TypedObjectTypingResult = TypingResultBase & TypedClass & {
    fields: Array<TypingResult>,
}

type TypedDict = TypingResultBase & {
    id: string,
    valueType: SingleTypingResult,
}

type TypedTaggedValue = (TypedObjectTypingResult | TypedDict | TypedClass) & {
    tag: string,
}

type SingleTypingResult = TypingResultBase & 
    (TypedObjectTypingResult | TypedDict | TypedTaggedValue | TypedClass)

type UnknownTyping = TypingResultBase & {
    params: Array<TypingResult>,
}

type UnionTyping = TypingResultBase & {
    union: Array<SingleTypingResult>,
}

export type TypingResult = UnknownTyping | SingleTypingResult | UnionTyping

export type UIParameter = {
     name: string,
     typ: TypingResult,
     editor: $TodoType,
     validators: $TodoType,
     additionalVariables: Record<string, TypingResult>,
     branchParam: boolean,
}

export type UITypedExpression = {
    name: string,
    typ: TypingResult,
}

export type SingleNodeConfig = {
    params?: Record<string, ParameterConfig>,
    icon?: string,
    docsUrl?: string,
    category?: string,
}

export type ParameterConfig = {
    defaultValue?: string,
    editor?: $TodoType,
    validators?: $TodoType,
    label?: string,
}

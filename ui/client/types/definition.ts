//types from pl.touk.nussknacker.ui.definition

export type UIObjectDefinition = {
    parameters: Array<UIParameter>,
    returnType?: TypingResult,
    categories: Array<string>,
    componentConfig: SingleComponentConfig,
}

interface TypingResultBase {
    type: string,
    display: string, 
}

export type TypedClass = {
    refClazzName: string,
    params: Array<TypingResult>, 
}

export type TypedObjectTypingResult = TypingResultBase & TypedClass & {
    fields: Record<string, TypingResult>,
}

export type TypedDict = TypingResultBase & {
    id: string,
    valueType: SingleTypingResult,
}

export type TypedTaggedValue = (TypedObjectTypingResult | TypedDict | TypedClass) & {
    tag: string,
}

export type SingleTypingResult = TypingResultBase &
    (TypedObjectTypingResult | TypedDict | TypedTaggedValue | TypedClass)

export type UnknownTyping = TypingResultBase & {
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
     defaultValue: string,
     additionalVariables: Record<string, TypingResult>,
     variablesToHide: Array<string>,
     branchParam: boolean,
}

export type SingleComponentConfig = {
    params?: Record<string, ParameterConfig>,
    icon?: string,
    docsUrl?: string,
    componentGroup?: string,
}

export type ParameterConfig = {
    defaultValue?: string,
    editor?: $TodoType,
    validators?: $TodoType,
    label?: string,
}

//types from pl.touk.nussknacker.ui.definition

export type UIObjectDefinition = {
    parameters: Array<UIParameter>,
    returnType?: TypingResult,
    categories: Array<string>,
    nodeConfig: SingleNodeConfig,
}

export type TypingResult = {
    type: string,
    display: string,
    refClazzName?: string,
    params?: TypingResult[],
}

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

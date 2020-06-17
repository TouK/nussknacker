//types from pl.touk.nussknacker.ui.definition

export type UIObjectDefinition = {
    parameters: Array<UIParameter>,
    returnType?: TypingResult,
    categories: Array<string>,
    nodeConfig: SingleNodeConfig,
}

export type TypingResult = $TodoType

export type UIParameter = {
    name: string,
     typ: TypingResult,
     editor: $TodoType,
     validators: $TodoType,
     additionalVariables: Record<string, TypingResult>,
     branchParam: boolean,
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

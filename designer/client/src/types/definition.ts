//types from pl.touk.nussknacker.ui.definition

import { ExpressionLang } from "../components/graph/node-modal/editors/expression/types";

interface TypingResultBase {
    value?: string | number | boolean;
    type: string;
    display: string;
    refClazzName: string;
}

interface TypedClass {
    refClazzName: string;
    params: Array<TypingResult>;
}

export type TypingInfo = Record<string, TypingResult>;

export interface TypedObjectTypingResult extends TypingResultBase, TypedClass {
    fields: TypingInfo;
}

interface TypedDict extends TypingResultBase {
    id: string;
    valueType: SingleTypingResult;
}

type TypedTaggedValue = (TypedObjectTypingResult | TypedDict | TypedClass) & {
    tag: string;
};

type SingleTypingResult = TypingResultBase & (TypedObjectTypingResult | TypedDict | TypedClass | TypedTaggedValue);

interface UnknownTyping extends TypingResultBase {
    params: TypingResult[];
}

interface UnionTyping extends TypingResultBase {
    union: Array<SingleTypingResult>;
}

export type TypingResult = UnknownTyping | SingleTypingResult | UnionTyping;

//TODO: Fixme, maybe we can use designer/client/src/components/graph/node-modal/editors/types.ts ?
export interface UIParameter {
    name: string;
    typ: TypingResult;
    editor: $TodoType;
    editors?: $TodoType;
    defaultValue: {
        language: ExpressionLang | string;
        expression: string;
    };
    additionalVariables: TypingInfo;
    variablesToHide: Array<string>;
    branchParam: boolean;
    hintText?: string;
    label: string;
}

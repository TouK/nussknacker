import type { TypingResult } from "../../../../types";
import type { PossibleValue } from "../aggregate/aggregatorFieldsStack";
import type { EditorMode, EditorType } from "./expression/types";

export type Editor = {
    type: `${EditorType}`;
    dictId?: string;
    possibleValues?: PossibleValue[];
};

export type ParamType = {
    name?: string;
    typ?: TypingResult;
    editors?: Editor[];
    defaultValue: {
        language: EditorMode | string;
        expression: string;
    };
    additionalVariables?: Record<string, unknown>;
    variablesToHide?: unknown[];
    branchParam?: boolean;
    hintText?: string | null;
    label?: string;
    requiredParam?: boolean;
};

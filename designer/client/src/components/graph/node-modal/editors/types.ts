import { TypingResult } from "../../../../types";
import { EditorMode, EditorType } from "./expression/types";
import { PossibleValue } from "../aggregate/aggregatorFieldsStack";

type Editor = {
    type: `${EditorType}`;
    dictId?: string;
    possibleValues?: PossibleValue;
};

export type ParamType = {
    name?: string;
    typ?: TypingResult;
    editor?: Editor;
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

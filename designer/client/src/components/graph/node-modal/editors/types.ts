import { TypingResult } from "../../../../types";
import { EditorType } from "./expression/Editor";
import { EditorMode } from "./expression/types";
import { PossibleValue } from "../aggregate/aggregatorFieldsStack";

type Editor = {
    type: `${EditorType}`;
    dictId?: string;
    possibleValues?: PossibleValue;
};

//TODO: FIXME
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

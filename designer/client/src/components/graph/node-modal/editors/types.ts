import { TypingResult } from "../../../../types";
import { EditorType } from "./expression/Editor";
import { EditorMode } from "./expression/types";

//TODO: FIXME
export type ParamType = {
    name?: string;
    typ?: TypingResult;
    editors?: {
        type: `${EditorType}`;
        dictId?: string;
    }[];
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

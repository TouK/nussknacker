import { TypingResult } from "../../../../types";
import { EditorType } from "./expression/Editor";
import { EditorMode, ExpressionLang } from "./expression/types";

//TODO: FIXME
export type ParamType = {
    name?: string;
    typ?: TypingResult;
    editor?: {
        type: `${EditorType}`;
    };
    editors?: {
        type: `${EditorType}`;
        language: ExpressionLang | string;
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

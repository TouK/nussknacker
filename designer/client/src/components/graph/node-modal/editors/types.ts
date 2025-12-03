import type { TypingResult } from "../../../../types/definition";
import type { EditorConfig } from "./expression/EditorConfig";
import type { EditorMode } from "./expression/types";

export type ParamType = {
    name?: string;
    typ?: TypingResult;
    editors?: EditorConfig[];
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

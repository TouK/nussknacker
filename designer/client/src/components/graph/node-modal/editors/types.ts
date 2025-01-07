import { ReturnedType } from "../../../../types";
import { EditorType } from "./expression/Editor";
import { EditorMode, ExpressionLang } from "./expression/types";

export type ParamType = {
    name: string;
    typ: ReturnedType;
    editors: {
        type: `${EditorType}`;
        language: ExpressionLang | string;
    }[];
    defaultValue: {
        language: EditorMode;
        expression: string;
    };
    additionalVariables: Record<string, unknown>;
    variablesToHide: unknown[];
    branchParam: boolean;
    hintText: string | null;
    label: string;
    requiredParam: boolean;
};

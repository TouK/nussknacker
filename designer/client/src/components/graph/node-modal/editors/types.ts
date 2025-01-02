import { ReturnedType } from "../../../../types";
import { EditorType } from "./expression/Editor";
import { EditorMode } from "./expression/types";

export type ParamType = {
    name: string;
    typ: ReturnedType;
    editor: {
        type: `${EditorType}`;
    };
    editors: {
        type: `${EditorType}`;
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

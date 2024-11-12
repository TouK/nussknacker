import { ReturnedType } from "../../../../types";
import { EditorType } from "./expression/Editor";

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
        language: "spel";
        expression: "";
    };
    additionalVariables: Record<string, unknown>;
    variablesToHide: unknown[];
    branchParam: boolean;
    hintText: string | null;
    label: string;
    requiredParam: boolean;
};

import { createContext, Dispatch, SetStateAction } from "react";
import { Expression, NodeValidationError, UIParameter, VariableTypes } from "../../../types";

export type ActionValues = Record<string, Expression>;

export const AdhocTestingFormContext = createContext<{
    value: ActionValues;
    setValue: Dispatch<SetStateAction<ActionValues>>;
    parameters: UIParameter[];
    variableTypes: VariableTypes;
    errors: NodeValidationError[];
}>(null);

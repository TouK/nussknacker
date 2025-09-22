import type { Dispatch, SetStateAction } from "react";
import { createContext } from "react";

import type { UIParameter } from "../../../types/definition";
import type { Expression } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";

export type ActionValues = Record<string, Expression>;

export const AdhocTestingFormContext = createContext<{
    value: ActionValues;
    setValue: Dispatch<SetStateAction<ActionValues>>;
    parameters: UIParameter[];
    variableTypes: VariableTypes;
    errors: NodeValidationError[];
}>(null);

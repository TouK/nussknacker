import { createContext } from "react";

/** Provided by EnricherProcessor; consumed by ParametersListField to show the builder button on the match condition input. */
export const ConditionBuilderContext = createContext<(() => void) | null>(null);

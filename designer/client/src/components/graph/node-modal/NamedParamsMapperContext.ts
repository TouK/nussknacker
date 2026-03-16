import { createContext } from "react";

/** Provided by NamedParamsDataMapper's parent; consumed by ParametersListField to show the builder button. */
export const NamedParamsMapperContext = createContext<((fieldName: string) => void) | null>(null);

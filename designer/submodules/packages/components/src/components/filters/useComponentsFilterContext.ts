import { useFilterContext } from "../../common";
import type { ComponentsFiltersModel } from "./componentsFiltersModel";
import { COMPONENTS_FILTER } from "./componentsFiltersModel";

export const useComponentsFilterContext = <T = ComponentsFiltersModel>() => useFilterContext<T>(COMPONENTS_FILTER);

import { useFilterContext } from "../../common/filters/filtersContext";
import type { ComponentsFiltersModel } from "./componentsFiltersModel";
import { COMPONENTS_FILTER } from "./componentsFiltersModel";

export const useComponentsFilterContext = <T = ComponentsFiltersModel>() => useFilterContext<T>(COMPONENTS_FILTER);

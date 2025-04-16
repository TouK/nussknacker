import { useFilterContext } from "../../common";
import type { UsagesFiltersModel } from "./usagesFiltersModel";
import { USAGES_FILTER } from "./usagesFiltersModel";

export const useUsagesFilterContext = <T = UsagesFiltersModel>() => useFilterContext<T>(USAGES_FILTER);

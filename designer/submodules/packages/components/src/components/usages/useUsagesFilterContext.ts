import { useFilterContext } from "../../common/filters/filtersContext";
import type { UsagesFiltersModel } from "./usagesFiltersModel";
import { USAGES_FILTER } from "./usagesFiltersModel";

export const useUsagesFilterContext = <T = UsagesFiltersModel>() => useFilterContext<T>(USAGES_FILTER);

import { useFilterContext } from "../../../common";
import type { ScenariosFiltersModel } from "../scenariosFiltersModel";
import { SCENARIOS_FILTER } from "../scenariosFiltersModel";

export const useScenariosFilterContext = <T = ScenariosFiltersModel>() => useFilterContext<T>(SCENARIOS_FILTER);

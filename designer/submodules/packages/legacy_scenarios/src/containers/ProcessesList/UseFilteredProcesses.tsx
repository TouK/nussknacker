import { isEqual } from "lodash";
import { useContext, useEffect, useMemo } from "react";
import { useDebounce } from "use-debounce";
import { normalizeParams } from "../../common/VisualizationUrl";
import { FiltersState } from "../TableFilters";
import { Queries } from "./types";
import { NkApiContext } from "../../settings/nkApiProvider";
import { useQuery } from "react-query";
import { useBaseIntervalTime } from "../../reducers/selectors/settings";

export function useFilteredProcesses(filters: FiltersState & Queries) {
    const normalizedFilters = useMemo(() => filters && normalizeParams(filters), [filters]);
    const [params] = useDebounce(normalizedFilters, 200, { equalityFn: isEqual });
    const api = useContext(NkApiContext);
    const refetchInterval = useBaseIntervalTime();

    const {
        data = [],
        refetch,
        isLoading,
    } = useQuery({
        queryKey: ["processes", params],
        queryFn: async () => {
            const hasParams = Object.keys(params).length;
            if (hasParams) {
                const { data } = await api.fetchProcesses(params);
                return data;
            }
        },
        refetchInterval,
        enabled: !!api,
    });

    return {
        processes: data,
        getProcesses: refetch,
        isLoading,
    };
}

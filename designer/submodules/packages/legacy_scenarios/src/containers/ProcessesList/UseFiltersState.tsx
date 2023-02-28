import { useCallback, useMemo, useState } from "react";
import { FiltersState } from "../TableFilters";
import { Queries } from "./types";

export const useFiltersState = (
    forcedQuery: Queries,
): {
    search: string;
    setFilters: (value: Partial<FiltersState>) => void;
    filters: Omit<FiltersState & Queries, "search">;
} => {
    const [_filters, _setFilters] = useState<FiltersState & Queries>({});
    const setFilters = useCallback(
        (value) => {
            _setFilters({ ...value, ...forcedQuery });
        },
        [forcedQuery],
    );

    const [search, filters] = useMemo(() => {
        if (_filters) {
            const { search, ...filters } = _filters;
            return [search, filters];
        }
        return [null, null];
    }, [_filters]);
    return { search, filters, setFilters };
};

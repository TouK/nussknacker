import React, { useContext, useMemo } from "react";
import { ProcessType } from "../../components/Process/types";
import styles from "../processesTable.styl";
import { ProcessesTable } from "../processesTable/ProcessesTable";
import { ProcessTableTools } from "../ProcessTableTools";
import { SearchQuery } from "../SearchQuery";
import { BaseProcessesOwnProps } from "./types";
import { useFilteredProcesses } from "./UseFilteredProcesses";
import { useFiltersState } from "./UseFiltersState";
import { useTextFilter } from "./UseTextFilter";
import { SearchContextProvider } from "../hooks/useSearchQuery";
import { NkApiContext } from "../../settings/nkApiProvider";
import { StatusesType } from "nussknackerUi/HttpService";
import { useQuery } from "react-query";

export const getProcessState = (statuses?: StatusesType) => (process: ProcessType) => statuses?.[process.name] || null;

export function ProcessesList(props: BaseProcessesOwnProps): JSX.Element {
    const { allowAdd, columns, RowsRenderer, filterable, defaultQuery, searchItems, sortable, withStatuses, children } = props;

    const { search, filters, setFilters } = useFiltersState(defaultQuery);
    const { processes, getProcesses, isLoading } = useFilteredProcesses(filters);

    const api = useContext(NkApiContext);
    const { data: statuses } = useQuery({
        queryKey: "statuses",
        queryFn: async () => {
            const { data } = await api.fetchProcessesStates();
            return data;
        },
        refetchInterval: 5000,
        enabled: !!withStatuses && !!processes?.length && !!api,
    });

    const filtered = useTextFilter(search, processes, filterable);

    const elements = useMemo(
        () => RowsRenderer({ processes: filtered, getProcesses, statuses }),
        [RowsRenderer, filtered, getProcesses, statuses],
    );

    const sortableValue = useMemo(
        () =>
            sortable.map((column) => ({
                column,
                sortFunction: Intl.Collator().compare,
            })),
        [sortable],
    );
    return (
        <SearchContextProvider>
            <ProcessTableTools allowAdd={allowAdd} isSubprocess={defaultQuery.isSubprocess}>
                <SearchQuery filters={searchItems} onChange={setFilters} />
            </ProcessTableTools>

            {children}

            <ProcessesTable className={styles.table} isLoading={isLoading} sortable={sortableValue} columns={columns}>
                {elements}
            </ProcessesTable>
        </SearchContextProvider>
    );
}

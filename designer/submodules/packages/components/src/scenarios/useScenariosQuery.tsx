import { UserData } from "nussknackerUi/common/models/User";
import { useContext, useMemo } from "react";
import { NkApiContext } from "../settings/nkApiProvider";
import { ProcessType } from "nussknackerUi/components/Process/types";
import { StatusesType } from "nussknackerUi/HttpService";
import { useQuery, useQueryClient } from "react-query";
import { UseQueryResult } from "react-query/types/react/types";
import { DateTime } from "luxon";

const scenarioStatusesQueryKey = "scenariosStatuses";

function useScenariosQuery(): UseQueryResult<ProcessType[]> {
    const api = useContext(NkApiContext);
    const queryClient = useQueryClient()
    return useQuery({
        queryKey: ["scenarios"],
        queryFn: async () => {
            const results = await api.fetchProcesses();
            const statuses = Object.fromEntries(results.data.map((scenario) => [scenario.name, scenario.state]));
            // Update statuses cache to reduce number of refetches
            queryClient.setQueryData(scenarioStatusesQueryKey, statuses);
            return results.data.map(({ createdAt, modificationDate, ...row }) => ({
                ...row,
                createdAt: createdAt && DateTime.fromISO(createdAt).toFormat("yyyy-MM-dd HH:mm:ss"),
                modificationDate: modificationDate && DateTime.fromISO(modificationDate).toFormat("yyyy-MM-dd HH:mm:ss"),
            }))
;
        },
        enabled: !!api,
        refetchInterval: 60000,
    });
}

export function useScenariosStatusesQuery(additionalEnabled: boolean = true): UseQueryResult<StatusesType> {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: [scenarioStatusesQueryKey],
        queryFn: async () => {
            const { data } = await api.fetchProcessesStates();
            return data;
        },
        enabled: !!api && additionalEnabled,
        refetchInterval: 15000,
        // We have to define staleTime because we set cache manually via queryClient.setQueryData (because we want to avoid unnecessary refetch)
        staleTime: 15000,
    });
}

export function useUserQuery(): UseQueryResult<UserData> {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: ["user"],
        queryFn: async () => {
            const { data } = await api.fetchLoggedUser();
            return data;
        },
        enabled: !!api,
        refetchInterval: 900000,
    });
}

export function useScenariosWithStatus(): UseQueryResult<ProcessType[]> {
    const scenarios = useScenariosQuery();
    const statuses = useScenariosStatusesQuery(!!scenarios.data);
    return useMemo(() => {
        const { data = [] } = scenarios;
        return {
            ...scenarios,
            data: data.map((scenario) => ({
                ...scenario,
                state: statuses?.data?.[scenario.id] || scenario.state,
            })),
        } as UseQueryResult<ProcessType[]>;
    }, [scenarios, statuses]);
}

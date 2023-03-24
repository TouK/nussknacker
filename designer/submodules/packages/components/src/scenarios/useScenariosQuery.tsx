import { UserData } from "nussknackerUi/common/models/User";
import { useContext, useEffect, useMemo } from "react";
import { NkApiContext } from "../settings/nkApiProvider";
import {ProcessType, StatusDefinitionType} from "nussknackerUi/components/Process/types";
import { StatusesType } from "nussknackerUi/HttpService";
import { useQuery, useQueryClient } from "react-query";
import { UseQueryResult } from "react-query/types/react/types";
import { DateTime } from "luxon";

const scenarioStatusesQueryKey = "scenariosStatuses";

function useScenariosQuery(): UseQueryResult<ProcessType[]> {
    const api = useContext(NkApiContext);
    const query = useQuery({
        queryKey: ["scenarios"],
        queryFn: async () => {
            const results = await api.fetchProcesses();
            return results.data.map(({ createdAt, modificationDate, ...row }) => ({
                ...row,
                createdAt: createdAt && DateTime.fromISO(createdAt).toFormat("yyyy-MM-dd HH:mm:ss"),
                modificationDate: modificationDate && DateTime.fromISO(modificationDate).toFormat("yyyy-MM-dd HH:mm:ss"),
            }));
        },
        enabled: !!api,
        refetchInterval: 60000,
    });

    const queryClient = useQueryClient();

    // Update statuses cache to reduce number of refetches
    useEffect(() => {
        const data = query.isFetched ? Object.fromEntries(query.data?.map((scenario) => [scenario.name, scenario.state])) : {};
        queryClient.setQueryData<StatusesType>(scenarioStatusesQueryKey, data);
    }, [query.dataUpdatedAt, query.data, query.isFetched, queryClient]);

    return query;
}

// FIXME: it's BE's job
function addDefaultIcon(data: StatusesType): StatusesType {
    const entries = Object.entries(data).map(([key, { icon, ...state }]) => [
        key,
        {
            ...state,
            icon: icon || "/assets/states/status-unknown.svg",
        },
    ]);
    return Object.fromEntries(entries);
}

export function useScenariosStatusesQuery(): UseQueryResult<StatusesType> {
    const api = useContext(NkApiContext);
    return useQuery<StatusesType>({
        queryKey: [scenarioStatusesQueryKey],
        queryFn: async () => {
            const { data } = await api.fetchProcessesStates();
            return data;
        },
        enabled: !!api,
        refetchInterval: 15000,
        // We have to define staleTime because we set cache manually via queryClient.setQueryData during fetching scenario
        // details (because we want to avoid unnecessary refetch)
        staleTime: 10000,
        select: addDefaultIcon,
    });
}

export function useStatusDefinitions(): UseQueryResult<StatusDefinitionType[]> {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: ["statusDefinitions"],
        queryFn: async () => {
            const { data } = await api.fetchStatusDefinitions();
            return data;
        },
        enabled: !!api,
        refetchInterval: false,
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
    const statuses = useScenariosStatusesQuery();
    return useMemo(() => {
        const { data = [] } = scenarios;
        return {
            ...scenarios,
            data: data.map((scenario) => ({
                ...scenario,
                state: statuses?.data?.[scenario.id],
            })),
        } as UseQueryResult<ProcessType[]>;
    }, [scenarios, statuses]);
}

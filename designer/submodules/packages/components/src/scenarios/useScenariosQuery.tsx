import { UserData } from "nussknackerUi/common/models/User";
import { useContext, useMemo } from "react";
import { NkApiContext } from "../settings/nkApiProvider";
import {ProcessType, StatusDefinitionType} from "nussknackerUi/components/Process/types";
import { StatusesType } from "nussknackerUi/HttpService";
import { useQuery } from "react-query";
import { UseQueryResult } from "react-query/types/react/types";
import { DateTime } from "luxon";

function useScenariosQuery(): UseQueryResult<ProcessType[]> {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: ["scenarios"],
        queryFn: async () => {
            const results = await api.fetchProcesses();
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

export function useScenariosStatusesQuery(): UseQueryResult<StatusesType> {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: ["scenariosStatuses"],
        queryFn: async () => {
            const { data } = await api.fetchProcessesStates();
            return data;
        },
        enabled: !!api,
        refetchInterval: 15000,
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
        refetchInterval: 15000,
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
                state: statuses?.data?.[scenario.id] || scenario.state,
            })),
        } as UseQueryResult<ProcessType[]>;
    }, [scenarios, statuses]);
}

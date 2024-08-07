import type { ComponentType, ComponentUsageType } from "nussknackerUi/HttpService";
import { useContext, useMemo } from "react";
import { useQuery } from "react-query";
import { UseQueryResult } from "react-query/types/react/types";
import { NkApiContext } from "../settings/nkApiProvider";
import { DateTime } from "luxon";
import { ProcessStateType } from "nussknackerUi/components/Process/types";
import { useScenariosStatusesQuery } from "../scenarios/useScenariosQuery";

export function useComponentsQuery(): UseQueryResult<ComponentType[]> {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: "components",
        queryFn: async () => {
            const { data } = await api.fetchComponents(false);
            return data;
        },
        enabled: !!api,
        refetchInterval: 60000,
    });
}

export function useComponentUsagesQuery(componentId: string): UseQueryResult<ComponentUsageType[]> {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: ["usages", componentId],
        queryFn: async () => {
            const { data } = await api.fetchComponentUsages(componentId);
            return data.map(({ createdAt, modificationDate, ...row }) => ({
                ...row,
                createdAt: createdAt && DateTime.fromISO(createdAt).toFormat("yyyy-MM-dd HH:mm:ss"),
                modificationDate: modificationDate && DateTime.fromISO(modificationDate).toFormat("yyyy-MM-dd HH:mm:ss"),
            }));
        },
        enabled: !!api,
        refetchInterval: 60000,
    });
}

export function useComponentQuery(componentId: string): UseQueryResult<ComponentType> {
    const query = useComponentsQuery();
    const component = useMemo(() => {
        return query.data?.find(({ id }) => id === componentId);
    }, [componentId, query.data]);

    return { ...query, data: component } as UseQueryResult<ComponentType>;
}

export interface UsageWithStatus extends ComponentUsageType {
    state: ProcessStateType;
}

export function useComponentUsagesWithStatus(componentId: string): UseQueryResult<UsageWithStatus[]> {
    const { data: usages = [], ...usagesQuery } = useComponentUsagesQuery(componentId);
    const { data: statuses, ...statusesQuery } = useScenariosStatusesQuery();
    return useMemo(() => {
        return {
            ...usagesQuery,
            data: usages.map((el) => ({
                ...el,
                state: statuses?.[el.name],
            })),
        } as UseQueryResult<UsageWithStatus[]>;
    }, [usagesQuery, usages, statuses]);
}

import { flatten, uniqBy } from "lodash";
import { useContext } from "react";
import { useQuery } from "react-query";
import { UseQueryResult } from "react-query/types/react/types";
import { NkApiContext, NkIconsContext } from "../settings/nkApiProvider";

export function useProcessesQuery() {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: "processes",
        queryFn: () => api.fetchProcesses(),
        enabled: !!api,
        refetchInterval: 10000,
    });
}

export interface ComponentType {
    service: any;
    icon: string;
    id: string;
    categories: string[];
    type: string;
    isUsed: boolean;
}

export function useComponentsQuery(): UseQueryResult<ComponentType[]> {
    const api = useContext(NkApiContext);
    const { getComponentIconSrc } = useContext(NkIconsContext);
    return useQuery({
        queryKey: "components",
        queryFn: async () => {
            const { data: unused } = await api.fetchUnusedComponents();
            const { data: services } = await api.fetchServices();
            const {
                data: { processingType },
            } = await api.fetchAppBuildInfo();

            const nodesGroups = await Promise.all(
                Object.keys(processingType).map(async (type) => {
                    const { data: process } = await api.fetchProcessDefinitionData(type, false);
                    const { data: subprocess } = await api.fetchProcessDefinitionData(type, true);
                    return flatten([
                        ...process.componentGroups.map((v) =>
                            v.components.map((n) => ({
                                ...n,
                                icon: getComponentIconSrc(n.node, process),
                            })),
                        ),
                        ...subprocess.componentGroups.map((v) =>
                            v.components.map((n) => ({
                                ...n,
                                icon: getComponentIconSrc(n.node, subprocess),
                            })),
                        ),
                    ]);
                }),
            );
            const uniq = uniqBy(flatten(nodesGroups), (n) => JSON.stringify([n.label, n.type]));

            const Services = Object.values(services).reduce((previousValue, currentValue) => ({ ...previousValue, ...currentValue }), {});
            const map: ComponentType[] = uniq.map((n) => ({
                id: n.label,
                type: n.type,
                icon: n.icon,
                categories: n.categories.sort(),
                isUsed: !unused.includes(n.label),
                service: Services[n.label],
            }));
            return map;
        },
        enabled: !!api,
        refetchInterval: 60000,
    });
}

import { useContext } from "react";
import { useQuery } from "react-query";
import { UseQueryResult } from "react-query/types/react/types";
import { NkApiContext, NkIconsContext } from "../settings/nkApiProvider";
import type { ComponentType } from "nussknackerUi/HttpService";

export function useComponentsQuery(): UseQueryResult<ComponentType[]> {
    const api = useContext(NkApiContext);
    const { getComponentIconSrc } = useContext(NkIconsContext);
    return useQuery({
        queryKey: "components",
        queryFn: async () => {
            const { data } = await api.fetchComponents();
            return data.map((component) => ({
                ...component,
                icon: getComponentIconSrc(component.icon),
            }));
        },
        enabled: !!api,
        refetchInterval: 60000,
    });
}

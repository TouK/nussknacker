import HttpService from "nussknackerUi/HttpService";
import { useContext } from "react";
import { useQuery } from "react-query";
import { UseQueryResult } from "react-query/types/react/types";
import { NkApiContext } from "./nkApiProvider";

export function useNkSettingsQuery(): UseQueryResult<Awaited<ReturnType<(typeof HttpService)["fetchSettingsWithAuth"]>>> {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: "settings",
        queryFn: () => api.fetchSettingsWithAuth(),
        enabled: !!api,
    });
}

import HttpService from "nussknackerUi/HttpService";
import { useContext } from "react";
import { useQuery } from "react-query";
import { UseQueryResult } from "react-query/types/react/types";
import { PromiseValue } from "type-fest";
import { NkApiContext } from "./nkApiProvider";

export function useNkSettingsQuery(): UseQueryResult<PromiseValue<ReturnType<typeof HttpService["fetchSettingsWithAuth"]>>> {
    const api = useContext(NkApiContext);
    return useQuery({
        queryKey: "settings",
        queryFn: () => api.fetchSettingsWithAuth(),
        enabled: !!api,
    });
}

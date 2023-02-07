import { useContext, useMemo } from "react";
import User from "../../common/models/User";
import { NkApiContext } from "../../settings/nkApiProvider";
import { useQuery } from "react-query";
import { useNkSettingsQuery } from "../../settings/useNkSettingsQuery";

export const useLoggedUser = () => {
    const api = useContext(NkApiContext);
    const { data: user, isFetched } = useQuery({
        queryKey: "user",
        queryFn: async () => {
            const { data } = await api.fetchLoggedUser();
            return data;
        },
        enabled: !!api,
    });
    return useMemo(() => (isFetched ? new User(user) : null), [isFetched, user]);
};

export const useBaseIntervalTime = () => {
    const { data: settings } = useNkSettingsQuery();
    return useMemo(
        () => settings?.features?.intervalTimeSettings?.processes || 15000,
        [settings?.features?.intervalTimeSettings?.processes],
    );
};

export const useHealthcheckIntervalTime = () => {
    const { data: settings } = useNkSettingsQuery();
    return useMemo(
        () => settings?.features?.intervalTimeSettings?.healthCheck || 40000,
        [settings?.features?.intervalTimeSettings?.healthCheck],
    );
};

export const useFilterCategories = () => {
    const user = useLoggedUser();
    return useMemo(
        () =>
            (user?.categories || [])
                .filter((c) => user.canRead(c))
                .map((e) => ({
                    value: e,
                    label: e,
                })),
        [user],
    );
};

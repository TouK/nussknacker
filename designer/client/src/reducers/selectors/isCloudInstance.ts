import { createSelector } from "reselect";
import { RootState } from "../index";
import { AuthStrategy } from "../settings";
import { getAuthenticationSettings } from "./settings";

export const getRemoteUrl = createSelector(getAuthenticationSettings, (auth) => {
    if (auth?.strategy !== AuthStrategy.REMOTE) return null;
    const exp = new RegExp("^managerWebAuth/auth@(?<url>https://.*cloud.nussknacker.io)/auth/remoteEntry.js$");
    const match = auth.moduleUrl?.match(exp);
    return match?.groups.url || null;
});

export const isCloudInstance = createSelector(getRemoteUrl, (url) => url !== null);

export const getAdditionalComponents = createSelector(
    isCloudInstance,
    (state: RootState) => state.cloudData.additionalComponents,
    (isCloud, additionalComponents) => (isCloud ? additionalComponents : []),
);

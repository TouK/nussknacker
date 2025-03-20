import { createSelector } from "reselect";
import { isDev } from "../../devHelpers";
import { RootState } from "../index";
import { AuthStrategy } from "../settings";
import { getAuthenticationSettings, getFeatureSettings } from "./settings";
import { getUserSettings } from "./userSettings";

const getRemoteEnv = createSelector(getAuthenticationSettings, (auth) => {
    if (auth?.strategy !== AuthStrategy.REMOTE) return null;
    const exp = new RegExp("^managerWebAuth/auth@https://manage\\.(?<env>.*cloud)\\.nussknacker\\.io/auth/remoteEntry\\.js$");
    const match = auth.moduleUrl?.match(exp);
    return match?.groups.env || null;
});

export const getRemoteWebHost = createSelector(getRemoteEnv, (env) => {
    return env ? `https://manage.${env}.nussknacker.io` : null;
});

export const getRemoteApiHost = createSelector(getRemoteEnv, (env) => {
    const host = isDev ? `nu.test.localhost:4000` : `nussknacker.io`;
    return env ? `${env}.${host}` : null;
});

export const getRemoteAppId = createSelector(getRemoteEnv, (env) => {
    return env ? `${env === "staging-cloud" ? "staging" : "production"}-tenants-gitlab-adapter` : null;
});

export const isCloudInstance = createSelector(getRemoteApiHost, (url) => url !== null);

export const getRemoteTenantId = createSelector(isCloudInstance, getFeatureSettings, (isCloud, fs) =>
    isCloud ? fs.usageStatisticsReports.fingerprint.replace(/^tenant-/, "") : null,
);

export const getAdditionalComponents = createSelector(
    isCloudInstance,
    getUserSettings,
    (state: RootState) => state.cloudData?.additionalComponents,
    (isCloud, userSettings, additionalComponents) =>
        isCloud && userSettings["cloud.showIntegrationsCreators"] ? additionalComponents : [],
);

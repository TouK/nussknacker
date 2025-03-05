import { useEffect } from "react";
import { init as initApm } from "@elastic/apm-rum";
import { useBuildInfo } from "../BuildInfoProvider";
import { useSelector } from "react-redux";
import { getFeatureSettings } from "../../reducers/selectors/settings";

export const useErrorRegister = () => {
    const buildInfo = useBuildInfo();
    const featuresSettings = useSelector(getFeatureSettings);
    const areErrorReportsEnabled = featuresSettings.usageStatisticsReports.errorReportsEnabled;
    const environment = featuresSettings.usageStatisticsReports.fingerprint;

    useEffect(() => {
        if (!areErrorReportsEnabled) {
            return;
        }

        const apm = initApm({
            serviceName: "Nu-designer-events",
            disableInstrumentations: ["fetch", "xmlhttprequest", "click", "history", "eventtarget", "page-load"],
            serverUrl: "https://apm.cloud.nussknacker.io",
            serverUrlPrefix: "/nu-events",
            serviceVersion: "1",
            environment,
        });

        apm.setCustomContext({ nuApiVersion: buildInfo.version, nuUiVersion: __BUILD_VERSION__ });
    }, [areErrorReportsEnabled, buildInfo?.version, environment]);
};

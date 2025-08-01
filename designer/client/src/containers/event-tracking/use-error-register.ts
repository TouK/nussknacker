import { init as initApm } from "@elastic/apm-rum";
import { useEffect } from "react";

import { getFeatureSettings } from "../../reducers/selectors/settings";
import { useBuildInfo } from "../BuildInfoProvider";
import { useAppSelector } from "./../../store/configureStore";

export const useErrorRegister = () => {
    const buildInfo = useBuildInfo();
    const featuresSettings = useAppSelector(getFeatureSettings);
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

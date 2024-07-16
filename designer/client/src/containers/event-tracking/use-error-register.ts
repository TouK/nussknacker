import { useEffect } from "react";
import { init as initApm } from "@elastic/apm-rum";
import { useBuildInfo } from "../BuildInfoProvider";

export const useErrorRegister = () => {
    const buildInfo = useBuildInfo();

    useEffect(() => {
        const apm = initApm({
            serviceName: "Nu-designer-events",
            disableInstrumentations: ["fetch", "xmlhttprequest", "click", "history", "eventtarget", "page-load"],
            serverUrl: "https://apm.cloud.nussknacker.io",
            serverUrlPrefix: "/nu-events",
            serviceVersion: "1",
            environment: "nussknacker-staging",
        });

        apm.setCustomContext({ nuApiVersion: buildInfo.version, nuUiVersion: __BUILD_VERSION__ });
    }, [buildInfo?.version]);
};

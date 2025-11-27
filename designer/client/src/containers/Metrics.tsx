import React, { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import type { ProcessName } from "src/components/Process/types";

import HttpService from "../http/HttpService/instance";
import { getMetricsSettings } from "../reducers/selectors/settings";
import { useAppSelector } from "../store/storeHelpers";
import { CustomTabWrapper } from "./CustomTabPage";

function useMetricsUrl(processName?: ProcessName): string {
    const [processingType, setProcessingType] = useState("");
    useEffect(() => {
        if (processName) {
            HttpService.fetchProcessDetails(processName).then(({ data }) => {
                setProcessingType(data.processingType);
            });
        } else {
            setProcessingType("");
        }
    }, [processName]);

    const settings = useAppSelector(getMetricsSettings);
    return useMemo(() => {
        const dashboard = settings.scenarioTypeToDashboard?.[processingType] || settings.defaultDashboard;
        const scenarioName = processName || "All";
        return settings.url?.replace("$dashboard", dashboard).replace("$scenarioName", scenarioName);
    }, [processName, processingType, settings]);
}

function Metrics(): React.JSX.Element {
    const { processName } = useParams<{ processName: string }>();
    const url = useMetricsUrl(processName);
    return <CustomTabWrapper tab={{ url, type: "IFrame", accessTokenInQuery: { enabled: true, parameterName: "auth_token" } }} />;
}

export default Metrics;

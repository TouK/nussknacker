import React, { useEffect, useMemo, useState } from "react";
import { useSelector } from "react-redux";
import { useParams } from "react-router-dom";
import HttpService from "../http/HttpService";
import { getMetricsSettings } from "../reducers/selectors/settings";
import { CustomTabWrapper } from "./CustomTabPage";
import { ProcessId } from "../types";

function useMetricsUrl(processId?: ProcessId): string {
    const [processingType, setProcessingType] = useState("");
    useEffect(() => {
        if (processId) {
            HttpService.fetchProcessDetails(processId).then(({ data }) => {
                setProcessingType(data.processingType);
            });
        } else {
            setProcessingType("");
        }
    }, [processId]);

    const settings = useSelector(getMetricsSettings);
    return useMemo(() => {
        const dashboard = settings.scenarioTypeToDashboard?.[processingType] || settings.defaultDashboard;
        const scenarioName = processId || "All";
        return settings.url?.replace("$dashboard", dashboard).replace("$scenarioName", scenarioName);
    }, [processId, processingType, settings]);
}

function Metrics(): JSX.Element {
    const { processId } = useParams<{ processId: string }>();
    const url = useMetricsUrl(processId);
    return <CustomTabWrapper tab={{ url, type: "IFrame", accessTokenInQuery: { enabled: true, parameterName: "auth_token" } }} />;
}

export default Metrics;

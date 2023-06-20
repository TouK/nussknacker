import { useSelector } from "react-redux";
import { getFeatureSettings } from "../reducers/selectors/settings";
import React from "react";

export function UsageReportingImage() {
    const featuresSettings = useSelector(getFeatureSettings);
    return (
        featuresSettings.usageStatisticsReports.enabled && (
            <img src={featuresSettings.usageStatisticsReports.url} alt="anonymous usage reporting" referrerPolicy="origin" hidden />
        )
    );
}

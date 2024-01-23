import React, { PropsWithChildren } from "react";
import { visualizationUrl } from "../common/VisualizationUrl";
import { ProcessName } from "src/components/Process/types";
import { PlainStyleLink } from "./plainStyleLink";

export function ProcessLink({
    processName,
    ...props
}: PropsWithChildren<{ processName: ProcessName; className?: string; title?: string }>): JSX.Element {
    return <PlainStyleLink to={visualizationUrl(processName)} {...props} />;
}

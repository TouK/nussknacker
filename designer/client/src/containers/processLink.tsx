import type { PropsWithChildren } from "react";
import React from "react";
import type { ProcessName } from "src/components/Process/types";

import { visualizationUrl } from "../common/VisualizationUrl";
import { PlainStyleLink } from "./plainStyleLink";

export function ProcessLink({
    processName,
    ...props
}: PropsWithChildren<{ processName: ProcessName; className?: string; title?: string }>): React.JSX.Element {
    return <PlainStyleLink to={visualizationUrl(processName)} {...props} />;
}

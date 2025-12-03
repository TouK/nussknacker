import React from "react";

import { getCapabilities } from "../../reducers/selectors/other";
import { useAppSelector } from "../../store/storeHelpers";
import { ToolbarButton } from "./toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "./toolbarButtons/types";

interface Props {
    write?: boolean;
    change?: boolean;
    deploy?: boolean;
    hide?: boolean;
    editFrontend?: boolean;
}

export const CapabilitiesToolbarButton = React.forwardRef<HTMLDivElement & HTMLButtonElement, ToolbarButtonProps & Props>(
    function CapabilitiesToolbarButton({ deploy, change, write, editFrontend, disabled, hide, ...props }, ref): React.JSX.Element | null {
        const capabilities = useAppSelector(getCapabilities);
        const checks = { deploy, change, write, editFrontend };
        const hiddenByCapabilities = Object.keys(capabilities).some((key) => checks[key] && !capabilities[key]);

        if (hide && hiddenByCapabilities) {
            return null;
        }

        const overridesProps = { ...props, ...{ disabled: disabled || hiddenByCapabilities } };

        return <ToolbarButton {...overridesProps} ref={ref} />;
    },
);

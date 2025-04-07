import type { PropsOf } from "@emotion/react";
import { Typography } from "@mui/material";
import React from "react";
import { useSelector } from "react-redux";

import { getHistoryCounts } from "../../../../reducers/selectors/getHistory";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";

type Props = PropsOf<typeof CapabilitiesToolbarButton> & {
    count?: number;
};

export const CounterToolbarButton = ({ count, ...props }: Props) => {
    const [, , snapshot] = useSelector(getHistoryCounts);
    const disabled = props.disabled || snapshot >= 0;
    return (
        <CapabilitiesToolbarButton {...props} disabled={disabled}>
            {disabled || count <= 1 ? null : (
                <Typography
                    variant="subtitle2"
                    sx={(theme) => ({ color: theme.palette.primary.main, position: "absolute", top: -5, right: -2 })}
                >
                    {count}
                </Typography>
            )}
        </CapabilitiesToolbarButton>
    );
};

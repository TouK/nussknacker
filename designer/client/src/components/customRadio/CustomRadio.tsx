import type { SvgIconComponent } from "@mui/icons-material";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import { Box, Checkbox, MenuItem, Paper, Typography } from "@mui/material";
import type { ComponentType, ForwardedRef, SVGProps } from "react";
import React, { forwardRef } from "react";

import { getBorderColor } from "../../containers/theme/helpers";

interface Props {
    label: string;
    value: string;
    Icon: SvgIconComponent | ComponentType<SVGProps<SVGSVGElement>>;
    disabled?: boolean;
    active?: boolean;
    title?: string;
}

export const CustomRadio = forwardRef(({ label, value, Icon, disabled, active, title }: Props, ref: ForwardedRef<HTMLButtonElement>) => {
    return (
        <Box component={"label"} flex={1} title={title}>
            <Checkbox disabled={disabled} sx={{ display: "none" }} checked={active} value={value} ref={ref} />
            <Paper
                component={MenuItem}
                variant={"outlined"}
                square
                disabled={disabled}
                sx={(theme) => ({
                    backgroundColor: theme.palette.background.paper,
                    p: [1, 2],
                    borderColor: active ? theme.palette.primary.main : getBorderColor(theme),
                    cursor: "pointer",
                    display: "flex",
                    justifyContent: "center",
                    alignItems: "center",
                    gap: 1,
                })}
            >
                <Icon />
                <Typography textTransform={"capitalize"} variant={"caption"} sx={{ cursor: "inherit" }}>
                    {label}
                </Typography>
                {active && (
                    <>
                        <Box
                            sx={(theme) => ({
                                backgroundColor: theme.palette.background.paper,
                                position: "absolute",
                                top: theme.spacing(-1.25),
                                right: theme.spacing(-1.25),
                                width: "1em",
                                height: "1em",
                            })}
                        />
                        <CheckCircleIcon
                            sx={(theme) => ({
                                position: "absolute",
                                top: theme.spacing(-1.25),
                                right: theme.spacing(-1.25),
                            })}
                            color={"primary"}
                        />
                    </>
                )}
            </Paper>
        </Box>
    );
});

CustomRadio.displayName = "CustomRadio";

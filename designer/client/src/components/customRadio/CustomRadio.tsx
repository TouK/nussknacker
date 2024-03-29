import React, { ComponentType, ForwardedRef, forwardRef, SVGProps } from "react";
import { Box, Checkbox, MenuItem, Paper, Typography } from "@mui/material";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import { SvgIconComponent } from "@mui/icons-material";

import { blendLighten } from "../../containers/theme/helpers";

interface Props {
    label: string;
    value: string;
    Icon: SvgIconComponent | ComponentType<SVGProps<SVGSVGElement>>;
    disabled?: boolean;
    active?: boolean;
}

export const CustomRadio = forwardRef(({ label, value, Icon, disabled, active }: Props, ref: ForwardedRef<HTMLButtonElement>) => {
    return (
        <Box component={"label"} flex={1}>
            <Checkbox disabled={disabled} sx={{ display: "none" }} checked={active} value={value} ref={ref} />
            <Paper
                component={MenuItem}
                variant={"outlined"}
                square
                disabled={disabled}
                sx={(theme) => ({
                    backgroundColor: theme.palette.background.paper,
                    p: [1, 2],
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

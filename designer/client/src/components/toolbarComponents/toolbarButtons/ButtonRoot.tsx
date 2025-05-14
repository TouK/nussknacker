import { Badge, Box, Typography } from "@mui/material";
import React, { forwardRef, useContext } from "react";

import { getEventTrackingProps, mapToolbarButtonToStatisticsEvent } from "../../../containers/event-tracking";
import { PANEL_BUTTON_SIZE, PANEL_BUTTON_SMALL_SIZE } from "../../../stylesheets/variables";
import { Button } from "./Button";
import { Icon } from "./Icon";
import { ButtonsVariant, ToolbarButtonsContext } from "./ToolbarButtons";
import type { ToolbarButtonProps } from "./types";

export const ButtonRoot = forwardRef<HTMLButtonElement, Omit<ToolbarButtonProps, "onDrop">>(function VariantWrapper(
    { title, name, icon, type, className, hasError, isActive, children, showIndicator, ...props },
    ref,
) {
    const { variant } = useContext(ToolbarButtonsContext);

    return (
        <Box
            className={className}
            sx={{
                pointerEvents: props.disabled ? "none" : null,
                position: "relative",
            }}
        >
            <Button
                {...props}
                ref={ref}
                {...getEventTrackingProps({ selector: mapToolbarButtonToStatisticsEvent(type) })}
                title={title || name}
                className={"toolbarButton-Root"}
                sx={(theme) => ({
                    width: [ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant)
                        ? "auto"
                        : `calc(${ButtonsVariant.small === variant ? PANEL_BUTTON_SMALL_SIZE : PANEL_BUTTON_SIZE}px - 2 * var(--margin))`,
                    padding:
                        variant === ButtonsVariant.horizontal
                            ? "4px 8px"
                            : [ButtonsVariant.small, ButtonsVariant.xs].includes(variant)
                            ? 0
                            : "4px 0",
                    flexDirection: [ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant) ? "row" : null,
                    zoom: variant === ButtonsVariant.xs ? 0.75 : null,
                    borderColor: hasError ? theme.palette.error.main : null,
                    color: hasError ? theme.palette.error.main : isActive ? theme.palette.success.main : null,
                })}
            >
                <Badge color="warning" overlap={"circular"} variant={showIndicator ? "dot" : null}>
                    <Icon
                        title={title}
                        className={"toolbarButton-Icon"}
                        sx={
                            variant === ButtonsVariant.horizontal
                                ? {
                                      "&, &>*": {
                                          flex: "none",
                                          height: "2em",
                                          width: "2em",
                                      },
                                  }
                                : null
                        }
                    >
                        {icon}
                    </Icon>
                </Badge>
                <Typography
                    variant={ButtonsVariant.horizontal === variant ? "button" : "overline"}
                    className={"toolbarButton-Label"}
                    sx={{
                        color: "inherit",
                        display: [ButtonsVariant.small, ButtonsVariant.xs].includes(variant) ? "none" : null,
                        whiteSpace: variant === ButtonsVariant.horizontal ? "nowrap" : null,
                        textTransform: variant === ButtonsVariant.label ? "lowercase" : null,
                        marginLeft: variant === ButtonsVariant.horizontal ? 1 : null,
                    }}
                >
                    {name}
                </Typography>
            </Button>
            {children}
        </Box>
    );
});

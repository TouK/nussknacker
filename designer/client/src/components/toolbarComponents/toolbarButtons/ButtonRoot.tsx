import { Badge, Box, Typography } from "@mui/material";
import React, { forwardRef, useContext } from "react";

import { getEventTrackingProps, mapToolbarButtonToStatisticsEvent } from "../../../containers/event-tracking";
import { PANEL_BUTTON_SIZE, PANEL_BUTTON_SMALL_SIZE } from "../../../stylesheets/variables";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip";
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
        <InfoTooltip
            title={title || ([ButtonsVariant.small, ButtonsVariant.xs].includes(variant) ? name : '')}
            variant={"hover"}
            customComponentsProps={{
                tooltip: {
                    sx: (theme) => ({
                        margin: `${theme.spacing(1)} !important`,
                    }),
                },
            }}
            enterDelay={500}
        >
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
                    aria-label={title || name}
                    className={"toolbarButton-Root"}
                    sx={(theme) => ({
                        width: [ButtonsVariant.menu, ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant)
                            ? "auto"
                            : `calc(${
                                  ButtonsVariant.small === variant ? PANEL_BUTTON_SMALL_SIZE : PANEL_BUTTON_SIZE
                              }px - 2 * var(--margin))`,
                        padding:
                            ButtonsVariant.menu === variant
                                ? "0 8px 0 0"
                                : ButtonsVariant.horizontal === variant
                                ? "4px 8px"
                                : [ButtonsVariant.small, ButtonsVariant.xs].includes(variant)
                                ? 0
                                : "4px 0",
                        flexDirection: [ButtonsVariant.menu, ButtonsVariant.horizontal, ButtonsVariant.xs].includes(variant) ? "row" : null,
                        zoom: variant === ButtonsVariant.xs ? 0.75 : null,
                        borderColor: hasError ? theme.palette.error.main : null,
                        color: hasError ? theme.palette.error.main : isActive ? theme.palette.success.main : null,
                    })}
                >
                    <Badge color="warning" overlap={"circular"} variant={showIndicator && variant === ButtonsVariant.xs ? "dot" : null}>
                        <Icon
                            title={title}
                            className={"toolbarButton-Icon"}
                            sx={
                                [ButtonsVariant.menu].includes(variant)
                                    ? {
                                          "&, &>*": {
                                              flex: "none",
                                              height: "1.5em",
                                              width: "1.5em",
                                          },
                                      }
                                    : [ButtonsVariant.horizontal].includes(variant)
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
                        variant={
                            [ButtonsVariant.menu].includes(variant)
                                ? "caption"
                                : [ButtonsVariant.horizontal].includes(variant)
                                ? "button"
                                : "overline"
                        }
                        className={"toolbarButton-Label"}
                        data-testid={"toolbarButton-label"}
                        sx={{
                            color: "inherit",
                            display: [ButtonsVariant.small, ButtonsVariant.xs].includes(variant) ? "none" : null,
                            whiteSpace: [ButtonsVariant.menu, ButtonsVariant.horizontal].includes(variant) ? "nowrap" : null,
                            textTransform: [ButtonsVariant.label].includes(variant) ? "lowercase" : "capitalize",
                            marginLeft: [ButtonsVariant.menu].includes(variant)
                                ? 0.5
                                : [ButtonsVariant.horizontal].includes(variant)
                                ? 1
                                : null,
                            position: "relative",
                            ...(showIndicator && {
                                "&::after": {
                                    content: '"*"',
                                    color: "warning.main",
                                    marginLeft: "2px",
                                    position: "absolute",
                                    right: -8, // Position to the right of text
                                    top: 0,
                                },
                            }),
                        }}
                    >
                        {name}
                    </Typography>
                </Button>
                {children}
            </Box>
        </InfoTooltip>
    );
});

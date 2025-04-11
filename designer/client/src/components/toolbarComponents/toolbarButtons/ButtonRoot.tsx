import { cx } from "@emotion/css";
import { Typography } from "@mui/material";
import React, { useContext } from "react";

import { getEventTrackingProps, mapToolbarButtonToStatisticsEvent } from "../../../containers/event-tracking";
import { PANEL_BUTTON_SIZE, PANEL_BUTTON_SMALL_SIZE } from "../../../stylesheets/variables";
import { Button } from "./Button";
import { Icon } from "./Icon";
import { ButtonsVariant, ToolbarButtonsContext } from "./ToolbarButtons";
import type { ToolbarButtonProps } from "./types";

export const ButtonRoot = React.forwardRef<HTMLDivElement & HTMLButtonElement, Omit<ToolbarButtonProps, "onDrop">>(function VariantWrapper(
    { title, name, icon, type, className, hasError, isActive, children, ...props },
    ref,
) {
    const { variant } = useContext(ToolbarButtonsContext);

    return (
        <Button
            ref={ref}
            {...props}
            {...getEventTrackingProps({ selector: mapToolbarButtonToStatisticsEvent(type) })}
            title={title || name}
            className={cx("toolbarButton-Root", className)}
            sx={(theme) => {
                const margin = 2;
                return {
                    margin: `${margin}px`,
                    borderRadius: `${3 * margin}px`,
                    width:
                        variant === ButtonsVariant.horizontal
                            ? "auto"
                            : ([ButtonsVariant.small, ButtonsVariant.xs].includes(variant) ? PANEL_BUTTON_SMALL_SIZE : PANEL_BUTTON_SIZE) -
                              2 * margin,
                    padding:
                        variant === ButtonsVariant.horizontal
                            ? "4px 8px"
                            : [ButtonsVariant.small, ButtonsVariant.xs].includes(variant)
                            ? 0
                            : null,
                    flexDirection: variant === ButtonsVariant.horizontal ? "row" : null,
                    zoom: variant === ButtonsVariant.xs ? 0.75 : null,

                    borderColor: hasError ? theme.palette.error.main : null,
                    color: hasError ? theme.palette.error.main : isActive ? theme.palette.success.main : null,

                    opacity: props.disabled ? 0.3 : 1,
                    "&:hover": {
                        backgroundColor: props.disabled ? "inherit" : theme.palette.action.hover,
                    },
                    "&, &:hover": {
                        cursor: props.disabled ? "inherit" : "pointer",
                    },
                };
            }}
        >
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
            <Typography
                variant={variant === ButtonsVariant.horizontal ? "button" : "overline"}
                className={"toolbarButton-Label"}
                sx={{
                    color: "inherit",
                    display: [ButtonsVariant.small, ButtonsVariant.xs].includes(variant) ? "none" : null,
                    whiteSpace: variant === ButtonsVariant.horizontal ? "nowrap" : null,
                    marginLeft: variant === ButtonsVariant.horizontal ? 1 : null,
                }}
            >
                {name}
            </Typography>
            {children}
        </Button>
    );
});

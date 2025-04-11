import { css, cx } from "@emotion/css";
import { Typography, useTheme } from "@mui/material";
import React, { useContext } from "react";
import Dropzone from "react-dropzone";

import { getEventTrackingProps, mapToolbarButtonToStatisticsEvent } from "../../../containers/event-tracking";
import { PANEL_BUTTON_SIZE, PANEL_BUTTON_SMALL_SIZE } from "../../../stylesheets/variables";
import { NodeInput } from "../../FormElements";
import type { ToolbarButtonProps } from "./index";
import { ButtonsVariant, ToolbarButtonsContext } from "./index";
import { Icon } from "./ToolbarButtonStyled";

// TODO: use MUI button for consistency
export const ToolbarButton = React.forwardRef<HTMLDivElement & HTMLButtonElement, ToolbarButtonProps>(function ToolbarButton(
    { onDrop, title, className, disabled, name, icon, hasError, isActive, type, ...props },
    ref,
) {
    const { variant } = useContext(ToolbarButtonsContext);
    const { palette } = useTheme();

    const margin = 2;
    const width = ([ButtonsVariant.small, ButtonsVariant.xs].includes(variant) ? PANEL_BUTTON_SMALL_SIZE : PANEL_BUTTON_SIZE) - 2 * margin;
    const styles = css({
        margin,
        padding:
            variant === ButtonsVariant.horizontal ? "4px 8px" : [ButtonsVariant.small, ButtonsVariant.xs].includes(variant) ? 0 : "4px 0",
        borderRadius: 6,
        display: "flex",
        flexDirection: variant === ButtonsVariant.horizontal ? "row" : "column",
        alignItems: "center",
        justifyContent: "start",
        border: "3px solid",
        userSelect: "none",
        opacity: disabled ? 0.3 : 1,
        cursor: disabled ? "not-allowed" : "pointer",
        width: variant === ButtonsVariant.horizontal ? "auto" : width,
        height: "fit-content",
        outline: "none",
        zoom: variant === ButtonsVariant.xs ? 0.75 : 1,

        borderColor: hasError ? palette.error.main : "transparent",

        color: hasError ? palette.error.main : isActive ? palette.success.main : palette.text.secondary,

        backgroundColor: palette.background.paper,
        ":hover": {
            backgroundColor: disabled ? "inherit" : palette.action.hover,
        },
    });

    const buttonProps = {
        ...props,
        ...getEventTrackingProps({ selector: mapToolbarButtonToStatisticsEvent(type) }),
        title: title || name,
        className: cx("toolbarButton-Root", styles, className),
        children: (
            <>
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
                        display: [ButtonsVariant.small, ButtonsVariant.xs].includes(variant) ? "none" : "unset",
                        whiteSpace: variant === ButtonsVariant.horizontal ? "nowrap" : "inherit",
                        marginLeft: variant === ButtonsVariant.horizontal ? 1 : "inherit",
                    }}
                >
                    {name}
                </Typography>
            </>
        ),
    };

    if (!disabled && onDrop) {
        return (
            <Dropzone onDrop={onDrop}>
                {({ getRootProps, getInputProps }) => (
                    <>
                        <div {...getRootProps(buttonProps)} />
                        <NodeInput {...getInputProps()} />
                    </>
                )}
            </Dropzone>
        );
    }

    return <button ref={ref} type="button" {...buttonProps} disabled={disabled} />;
});

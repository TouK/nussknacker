import { css, cx } from "@emotion/css";
import { Typography, useTheme } from "@mui/material";
import React, { useContext } from "react";
import Dropzone from "react-dropzone";
import { PANEL_BUTTON_SIZE, PANEL_BUTTON_SMALL_SIZE } from "../../../stylesheets/variables";
import { NodeInput } from "../../FormElements";
import { ButtonsVariant, ToolbarButtonProps, ToolbarButtonsContext } from "./index";
import { Icon } from "./ToolbarButtonStyled";
import { EventTrackingType, getEventTrackingProps, mapToolbarButtonToStatisticsEvent } from "../../../containers/event-tracking";

export const ToolbarButton = React.forwardRef<HTMLDivElement & HTMLButtonElement, ToolbarButtonProps>(function ToolbarButton(
    { onDrop, title, className, disabled, name, icon, hasError, isActive, type, ...props },
    ref,
) {
    const { variant } = useContext(ToolbarButtonsContext);
    const { palette } = useTheme();

    const margin = 2;
    const width = (variant === ButtonsVariant.small ? PANEL_BUTTON_SMALL_SIZE : PANEL_BUTTON_SIZE) - 2 * margin;
    const styles = css({
        margin,
        padding: variant === ButtonsVariant.small ? 0 : "4px 0",
        borderRadius: 6,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "start",
        border: "3px solid",
        userSelect: "none",
        opacity: disabled ? 0.3 : 1,
        cursor: disabled ? "not-allowed" : "pointer",
        width,
        height: "fit-content",
        outline: "none",

        borderColor: hasError ? palette.error.main : "transparent",

        color: hasError ? palette.error.main : isActive ? palette.success.main : palette.text.secondary,

        backgroundColor: palette.background.paper,
        ":hover": {
            backgroundColor: disabled ? "inherit" : palette.action.hover,
        },
    });

    const buttonProps = {
        ...props,
        ...getEventTrackingProps({ selector: mapToolbarButtonToStatisticsEvent(type), event: EventTrackingType.CLICK }),
        title: title || name,
        className: cx(styles, className),
        children: (
            <>
                <Icon title={title}>{icon}</Icon>
                <Typography variant={"overline"} display={variant === ButtonsVariant.small ? "none" : "unset"}>
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

import { MoreVert } from "@mui/icons-material";
import { IconButton, Popover } from "@mui/material";
import i18next from "i18next";
import React, { memo, useRef, useState } from "react";

import { useDragHandler } from "../../common/dndItems/DragHandle";
import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ButtonsVariant, ToolbarButtons } from "../../toolbarComponents/toolbarButtons/ToolbarButtons";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { ScenarioStatusContent } from "./ScenarioStatusContent";

const ScenarioStatusPanel = memo(function ScenarioStatusPanel({ buttonsVariant, children, ...props }: ToolbarPanelProps) {
    const [buttonsVisible, setButtonsVisible] = useState<HTMLButtonElement | null>(null);
    const handleProps = useDragHandler();

    const ref = useRef<HTMLDivElement>(null);

    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.scenarioDetails.title", "Scenario details")} disableCollapse>
            <ScenarioStatusContent {...handleProps}>
                <IconButton
                    color="inherit"
                    onClick={(e) => setButtonsVisible(e.currentTarget)}
                    sx={(theme) => ({
                        position: "absolute",
                        top: theme.spacing(1),
                        right: theme.spacing(1),
                        borderRadius: theme.spacing(0.5),
                    })}
                >
                    <MoreVert />
                </IconButton>
            </ScenarioStatusContent>
            <Popover
                open={Boolean(buttonsVisible)}
                anchorEl={buttonsVisible}
                anchorOrigin={{
                    vertical: "bottom",
                    horizontal: "left",
                }}
                onClose={() => setButtonsVisible(null)}
                keepMounted
            >
                <ToolbarButtons
                    ref={ref}
                    variant={ButtonsVariant.menu}
                    onClick={() => setButtonsVisible(null)}
                    sx={{
                        flexDirection: "column",
                        padding: 0.5,
                        ".toolbarButton-Root": {
                            justifySelf: "stretch",
                        },
                    }}
                >
                    {children}
                </ToolbarButtons>
            </Popover>
        </ToolbarWrapper>
    );
});

export default ScenarioStatusPanel;

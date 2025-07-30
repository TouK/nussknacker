import { AddBoxOutlined } from "@mui/icons-material";
import { Fade, IconButton, styled } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { PanelSide } from "../actions/nk";
import LeftIcon from "../assets/img/arrows/arrow-left.svg";
import RightIcon from "../assets/img/arrows/arrow-right.svg";
import { RECT_HEIGHT, RECT_WIDTH } from "./graph/EspNode/esp";
import { useGraph } from "./graph/GraphContext";
import { useSidePanel } from "./sidePanels/SidePanelsContext";
import { globalEventBus } from "./toolbars/creator/globalEventBus";
import { ComponentFilter } from "./toolbars/creator/ToolBox";

const IconWrapper = styled(IconButton)(({ theme }) => ({
    borderRadius: 0,
    transition: theme.transitions.create(["left", "right"], {
        duration: theme.transitions.duration.short,
        easing: theme.transitions.easing.easeInOut,
    }),
}));

type Props = {
    type: PanelSide;
};

export function SidePanelToggleButton({ type, ...props }: Props) {
    const { t } = useTranslation();
    const { isOpened, switchVisible, toggleCollapse } = useSidePanel(type);
    const left = [PanelSide.Left, PanelSide.LeftDynamic].includes(type) ? isOpened : !isOpened;
    const title = [PanelSide.Left, PanelSide.LeftDynamic].includes(type)
        ? t("panel.toggle.left", "toggle left panel")
        : t("panel.toggle.right", "toggle right panel");

    return (
        <Fade in={switchVisible}>
            <IconWrapper title={title} onClick={toggleCollapse} disableFocusRipple color="inherit" size="small" {...props}>
                {left ? <LeftIcon /> : <RightIcon />}
            </IconWrapper>
        </Fade>
    );
}
export function SidePanelToggleButton2({ placeholder }: { placeholder?: boolean }) {
    const { t } = useTranslation();
    const title = placeholder ? t("panels.creator.openSelectFirst", "add source node") : t("panels.creator.openSelect", "add new node");
    const graphGetter = useGraph();

    return (
        <IconWrapper
            title={title}
            onClick={() => {
                const paper = graphGetter().processGraphPaper;
                const center = paper.clientToLocalPoint(graphGetter().viewport.center());
                globalEventBus.emit("openNodeSelector", {
                    side: PanelSide.RightDynamic,
                    filters: placeholder ? [ComponentFilter.sourcesOnly] : [],
                    fromPoint: center.offset(RECT_WIDTH * -0.5, RECT_HEIGHT * -0.5),
                });
            }}
            disableFocusRipple
            color="inherit"
            size="small"
            disableRipple={placeholder}
            disableTouchRipple={placeholder}
            sx={{
                zoom: placeholder ? 3 : 1,
                opacity: placeholder ? 0.2 : 1,
            }}
        >
            <AddBoxOutlined fontSize="large" />
        </IconWrapper>
    );
}

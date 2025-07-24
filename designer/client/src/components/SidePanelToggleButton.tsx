import { Fade, IconButton, styled } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { PanelSide } from "../actions/nk";
import LeftIcon from "../assets/img/arrows/arrow-left.svg";
import RightIcon from "../assets/img/arrows/arrow-right.svg";
import { useSidePanel } from "./sidePanels/SidePanelsContext";

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

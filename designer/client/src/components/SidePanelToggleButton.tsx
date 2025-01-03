import { Fade, IconButton, styled, Theme } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";
import LeftIcon from "../assets/img/arrows/arrow-left.svg";
import RightIcon from "../assets/img/arrows/arrow-right.svg";
import { PanelSide } from "../actions/nk";
import { useSidePanel } from "./sidePanels/SidePanelsContext";
import { SxProps } from "@mui/system";

const IconWrapper = styled(IconButton)(({ theme }) => ({
    borderRadius: 0,
    transition: theme.transitions.create(["left", "right"], {
        duration: theme.transitions.duration.short,
        easing: theme.transitions.easing.easeInOut,
    }),
}));

type Props = {
    type: PanelSide;
    handleStyle?: (isOpened: boolean) => SxProps<Theme>;
};

export function SidePanelToggleButton({ type, handleStyle, ...props }: Props) {
    const { t } = useTranslation();
    const { isOpened, switchVisible, toggleCollapse } = useSidePanel(type);
    const left = type === PanelSide.Left ? isOpened : !isOpened;
    const title = type === PanelSide.Left ? t("panel.toggle.left", "toggle left panel") : t("panel.toggle.right", "toggle right panel");

    return (
        <Fade in={switchVisible}>
            <IconWrapper
                title={title}
                onClick={toggleCollapse}
                disableFocusRipple
                color="inherit"
                size="small"
                sx={handleStyle?.(isOpened)}
                {...props}
            >
                {left ? <LeftIcon /> : <RightIcon />}
            </IconWrapper>
        </Fade>
    );
}

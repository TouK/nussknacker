import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import { Button, styled, Zoom } from "@mui/material";
import { useContentScroll } from "@touk/window-manager";
import React from "react";
import { useDebouncedValue } from "rooks";

const StyledScrollButton = styled(Button)(({ theme }) => ({
    position: "fixed",
    bottom: 90,
    left: "50%",
    transform: "translateX(-50%)",
    zIndex: 1000,
    minWidth: 0,
    padding: theme.spacing(0.5),
    borderRadius: "50%",
}));

function useDelayedIn(show: boolean | unknown, delay = 500) {
    const [showDebounced] = useDebouncedValue(Boolean(show), delay);
    return show && showDebounced;
}

export const ScrollToBottomButton = () => {
    const { hasBottomOverflow, scrollToBottom } = useContentScroll();
    return (
        <Zoom in={useDelayedIn(hasBottomOverflow())}>
            <StyledScrollButton variant="contained" size="small" onClick={scrollToBottom}>
                <ArrowDownwardIcon />
            </StyledScrollButton>
        </Zoom>
    );
};

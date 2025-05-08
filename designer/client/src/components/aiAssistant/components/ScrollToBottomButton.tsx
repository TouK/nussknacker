import { useThreadViewport } from "@assistant-ui/react";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import { Button, styled } from "@mui/material";
import React, { useState, useEffect } from "react";

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

export const ScrollToBottomButton = () => {
    const { scrollToBottom } = useThreadViewport();
    const [showButton, setShowButton] = useState(false);

    useEffect(() => {
        const scrollContainer = document.querySelector(`[data-testid="window"] section`);
        if (!scrollContainer) return;

        const checkScroll = () => {
            if (scrollContainer.scrollHeight > scrollContainer.clientHeight) {
                const atBottom = scrollContainer.scrollTop + scrollContainer.clientHeight >= scrollContainer.scrollHeight - 5;
                setShowButton(!atBottom);
            } else {
                setShowButton(false);
            }
        };

        checkScroll();
        scrollContainer.addEventListener("scroll", checkScroll);
        window.addEventListener("resize", checkScroll);

        return () => {
            scrollContainer.removeEventListener("scroll", checkScroll);
            window.removeEventListener("resize", checkScroll);
        };
    }, []);

    const handleClick = () => {
        scrollToBottom();
    };

    if (!showButton) return null;

    return (
        <StyledScrollButton variant="contained" size="small" onClick={handleClick}>
            <ArrowDownwardIcon />
        </StyledScrollButton>
    );
};

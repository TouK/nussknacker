import { ActionBarPrimitive } from "@assistant-ui/react";
import { styled } from "@mui/material";
import { debounce } from "lodash";
import type { PropsWithChildren } from "react";
import { useCallback, useMemo } from "react";
import React from "react";

export const useHandleActions = () => {
    const [showActions, setShowActions] = React.useState<boolean>(false);

    const handleShowActions = useCallback(() => {
        setShowActions(true);
    }, []);

    const handleHideActions = useMemo(() => {
        return debounce(() => {
            setShowActions(false);
        }, 100);
    }, []);

    return { showActions, handleShowActions, handleHideActions };
};
const StyledActionContainer = styled(ActionBarPrimitive.Root, {
    shouldForwardProp: (prop) => prop !== "show",
})<{ placement: Props["placement"]; show: Props["show"] }>(({ theme, placement, show }) => ({
    display: show ? "flex" : "none",
    position: "absolute",
    alignSelf: "flex-end",
    bottom: theme.spacing(-1.25),
    padding: theme.spacing(0.5),
    ...(placement === "left" ? { left: theme.spacing(0) } : { right: theme.spacing(0) }),
}));

interface Props {
    show: boolean;
    placement?: "left" | "right";
}
export const ActionsContainer = ({ children, show = false, placement = "right" }: PropsWithChildren<Props>) => {
    return (
        <StyledActionContainer show={show} placement={placement}>
            {children}
        </StyledActionContainer>
    );
};

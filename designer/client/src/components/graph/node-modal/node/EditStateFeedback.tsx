import { alpha, Box, Grow, styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useTimeoutWhen } from "rooks";

import type { EditState } from "./useNodeState";

function Toast({ isVisible, children }: PropsWithChildren<{ isVisible: boolean }>) {
    return (
        <Grow in={isVisible} mountOnEnter unmountOnExit>
            <Box
                sx={(theme) => ({
                    position: "fixed",
                    top: 0,
                    right: 0,
                    zIndex: theme.zIndex.tooltip,
                    display: "flex",
                    justifyContent: "center",
                    pointerEvents: "none",
                })}
            >
                {children}
            </Box>
        </Grow>
    );
}

const Alert = styled("div")(({ theme }) => ({
    margin: theme.spacing(1),
    textTransform: "uppercase",
    fontSize: ".5em",
    fontWeight: "bold",
    padding: ".5em 1em",
    boxShadow: theme.shadows[3],
    borderRadius: theme.shape.borderRadius,
    background: alpha(theme.palette.background.paper, 0.9),
    color: theme.palette.getContrastText(theme.palette.background.paper),
    backdropFilter: "blur(2px)",
}));

const SuccessAlert = styled(Alert)(({ theme }) => ({
    background: alpha(theme.palette.success.main, 0.9),
    color: theme.palette.getContrastText(theme.palette.success.main),
}));

export function EditStateFeedback({ editState }: { editState: EditState }) {
    const prev = useRef(editState);
    const [idleVisible, setIdleVisible] = useState(false);
    useTimeoutWhen(() => setIdleVisible(false), 1000, idleVisible);
    useEffect(() => {
        setIdleVisible(editState === "idle" && prev.current !== "idle");
        prev.current = editState;
    }, [editState]);

    const { t } = useTranslation();

    return (
        <>
            <Toast isVisible={!idleVisible && editState !== "idle"}>
                <Alert>{t("node.edit.pending", "pending changes...")}</Alert>
            </Toast>
            <Toast isVisible={idleVisible}>
                <SuccessAlert>{t("node.edit.applied", "changes applied")}</SuccessAlert>
            </Toast>
        </>
    );
}

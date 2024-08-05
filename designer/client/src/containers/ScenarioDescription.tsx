import { useSelector } from "react-redux";
import { getScenarioDescription } from "../reducers/selectors/graph";
import { alpha, Grow, IconButton, Paper } from "@mui/material";
import { Close, Description } from "@mui/icons-material";
import { MarkdownStyled } from "../components/graph/node-modal/MarkdownStyled";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

export const ScenarioDescription = () => {
    const [description, showDescription] = useSelector(getScenarioDescription);
    const [expanded, setExpanded] = useState(showDescription);

    const toggle = () => setExpanded((v) => !v);
    const { t } = useTranslation();
    const title = t("graph.description.toggle", "toggle description view");

    return (
        <>
            <Grow in={description && !expanded} unmountOnExit>
                <IconButton
                    title={title}
                    onClick={toggle}
                    sx={{
                        borderRadius: 0,
                    }}
                    disableFocusRipple
                    color="inherit"
                >
                    <Description />
                </IconButton>
            </Grow>
            <Grow in={description && expanded} unmountOnExit style={{ transformOrigin: "0 0 0" }}>
                <Paper
                    sx={{
                        position: "absolute",
                        top: 0,
                        left: 0,
                        right: 0,
                        maxWidth: (t) => t.breakpoints.values.md * 0.9,
                        background: (t) => alpha(t.palette.background.paper, 0.75),
                        backdropFilter: "blur(25px)",
                        margin: 0,
                        padding: 1,
                        display: "flex",
                        flexGrow: 0,
                        flexShrink: 1,
                    }}
                >
                    <IconButton
                        onClick={toggle}
                        sx={{
                            position: "absolute",
                            top: 0,
                            left: 0,
                            borderRadius: 0,
                        }}
                        disableFocusRipple
                        color="inherit"
                        size="small"
                    >
                        <Close />
                    </IconButton>
                    <MarkdownStyled
                        sx={{
                            margin: 0,
                            padding: 2,
                            overflow: "auto",
                        }}
                    >
                        {description}
                    </MarkdownStyled>
                </Paper>
            </Grow>
        </>
    );
};

import { useSelector } from "react-redux";
import { getScenarioDescription } from "../reducers/selectors/graph";
import { alpha, Grow, IconButton, Paper } from "@mui/material";
import { Close, Description } from "@mui/icons-material";
import { MarkdownStyled } from "../components/graph/node-modal/MarkdownStyled";
import React, { useState } from "react";

export const ScenarioDescription = () => {
    const [expanded, setExpanded] = useState(false);
    const description = useSelector(getScenarioDescription);
    const toggle = () => setExpanded((v) => !v);
    return (
        <>
            <Grow in={description && !expanded} unmountOnExit>
                <IconButton
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
                        padding: 0,
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
                            right: 0,
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

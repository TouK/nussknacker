import { useSelector } from "react-redux";
import { getScenarioDescription } from "../reducers/selectors/graph";
import { alpha, Box, Fade, Paper, styled } from "@mui/material";
import { Close, Description } from "@mui/icons-material";
import { MarkdownStyled } from "../components/graph/node-modal/MarkdownStyled";
import React, { useState } from "react";

const AbsolutePaper = styled(Box)(({ theme }) => ({
    position: "absolute",
    top: 0,
    left: 0,
    zIndex: theme.zIndex.fab,
    margin: theme.spacing(2),
    transition: theme.transitions.create(["left", "right"], {
        duration: theme.transitions.duration.short,
        easing: theme.transitions.easing.easeInOut,
    }),
    maxWidth: theme.breakpoints.values.md,
}));

export const ScenarioDescription = ({ left = 0, right = 0, top = 0 }) => {
    const [expanded, setExpanded] = useState(true);
    const description = useSelector(getScenarioDescription);
    const toggle = () => setExpanded((v) => !v);
    return (
        <AbsolutePaper style={{ top, left, right }}>
            <Fade in={description && !expanded}>
                <Description onClick={toggle} sx={{ position: "absolute", top: 0, left: 0 }} />
            </Fade>
            <Fade in={description && expanded}>
                <Paper
                    sx={{
                        overflow: "hidden",
                        position: "relative",
                        background: (t) => alpha(t.palette.background.paper, 0.75),
                        backdropFilter: "blur(25px)",
                    }}
                >
                    <Close onClick={toggle} sx={{ position: "absolute", top: 0, right: 0 }} />
                    <MarkdownStyled sx={{ margin: 2 }}>{description}</MarkdownStyled>
                </Paper>
            </Fade>
        </AbsolutePaper>
    );
};

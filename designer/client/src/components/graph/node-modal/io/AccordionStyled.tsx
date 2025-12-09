import { Accordion, type AccordionProps, keyframes, styled } from "@mui/material";
import { blend } from "@mui/system";

export const AccordionStyled = styled(Accordion, {
    shouldForwardProp: (prop) => prop !== "animatedAppear",
})<AccordionProps & { animatedAppear: boolean }>(({ theme, expanded, animatedAppear }) => ({
    // gutter sized shadow to mask sticky elements in margin area
    boxShadow: expanded ? `0 0 0 ${theme.spacing(2)} var(--sidePanelBackground)` : null,
    "&:first-of-type": {
        boxShadow: expanded ? `0 ${theme.spacing(1)} 0 ${theme.spacing(1)} var(--sidePanelBackground)` : null,
    },
    "&:last-of-type": {
        boxShadow: expanded ? `0 -${theme.spacing(1)} 0 ${theme.spacing(1)} var(--sidePanelBackground)` : null,
    },
    transitionProperty: "transform, box-shadow, margin",
    "&.highlight": {
        animation: [
            `${keyframes({
                from: {
                    // avoid transparency for performace
                    backgroundColor: blend(theme.palette.success.main, theme.palette.background.paper, 0.75),
                },
                to: {
                    backgroundColor: theme.palette.background.paper,
                },
            })} 5s ease`,
            `${keyframes({
                from: {
                    filter: "brightness(150%) saturate(300%)",
                },
                to: {
                    filter: "none",
                },
            })} 1.5s ease`,
        ].join(","),
    },
}));

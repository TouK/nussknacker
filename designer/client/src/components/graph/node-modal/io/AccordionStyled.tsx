import { Accordion, type AccordionProps, keyframes, styled } from "@mui/material";
import { blend } from "@mui/system";

export const AccordionStyled = styled(Accordion, {
    shouldForwardProp: (prop) => prop !== "animatedAppear",
})<AccordionProps & { animatedAppear: boolean }>(({ theme, expanded, animatedAppear }) => ({
    zoom: 0.75,
    // gutter sized shadow to mask sticky elements in margin area
    boxShadow: expanded ? `0 0 0 ${theme.spacing(2)} var(--sidePanelBackground)` : null,
    animation: animatedAppear
        ? [
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
          ].join(",")
        : null,
}));

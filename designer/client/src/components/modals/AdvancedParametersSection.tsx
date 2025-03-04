import React, { PropsWithChildren } from "react";
import Accordion from "@mui/material/Accordion";
import AccordionSummary from "@mui/material/AccordionSummary";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Typography } from "@mui/material";
import AccordionDetails from "@mui/material/AccordionDetails";

interface Props {
    componentId: string;
}

export function AdvancedParametersSection({ children, componentId }: PropsWithChildren<Props>): JSX.Element {
    return (
        <Accordion disableGutters elevation={0} sx={{ border: 0, "&::before": { display: "none" } }}>
            <AccordionSummary
                expandIcon={<ExpandMoreIcon sx={{ color: "inherit" }} />}
                aria-controls={`${componentId}-content`}
                id={`${componentId}-header`}
                sx={{ flexDirection: "row-reverse", border: 0 }}
            >
                <Typography>{componentId}</Typography>
            </AccordionSummary>
            <AccordionDetails>{children}</AccordionDetails>
        </Accordion>
    );
}

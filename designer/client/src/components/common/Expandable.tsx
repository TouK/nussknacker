import React, { PropsWithChildren } from "react";
import Accordion from "@mui/material/Accordion";
import AccordionSummary from "@mui/material/AccordionSummary";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Typography } from "@mui/material";
import AccordionDetails from "@mui/material/AccordionDetails";
import { SxProps } from "@mui/system";
import { Theme } from "@mui/material/styles";

interface Props {
    componentId: string;
    expandableTitle: string;
    onChange: (isExpanded: boolean) => void;
    expanded?: boolean;
    expandIconSx?: SxProps<Theme>;
    typographySx?: SxProps<Theme>;
}

export function Expandable({
    children,
    componentId,
    expandableTitle,
    onChange,
    expanded,
    expandIconSx={ color: "inherit" },
    typographySx,
}: PropsWithChildren<Props>): JSX.Element {
    return (
        <Accordion
            disableGutters
            elevation={0}
            sx={{ border: 0, "&::before": { display: "none" } }}
            onChange={(_, isExpanded) => onChange(isExpanded)}
            expanded={expanded}
        >
            <AccordionSummary
                expandIcon={<ExpandMoreIcon sx={expandIconSx} />}
                aria-controls={`${componentId}-content`}
                id={`${componentId}-header`}
                sx={{ flexDirection: "row-reverse", border: 0, padding: 0 }}
            >
                <Typography sx={typographySx}>{expandableTitle}</Typography>
            </AccordionSummary>
            <AccordionDetails>{children}</AccordionDetails>
        </Accordion>
    );
}

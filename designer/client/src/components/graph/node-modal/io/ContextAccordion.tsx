import { ExpandMore } from "@mui/icons-material";
import { Accordion, AccordionDetails, AccordionSummary } from "@mui/material";
import type { PropsWithChildren } from "react";
import { memo, ReactNode } from "react";
import { useRef } from "react";
import React, { useLayoutEffect } from "react";
import { useDispatch } from "react-redux";

import { Initiator, startLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import { ContextTitle } from "./ContextTitle";
import type { Direction, VariableContextType } from "./VariableContextTree";

export const ContextAccordion = memo(function ContextAccordion({
    disabled,
    expanded,
    onToggle,
    children,
    value,
    direction,
    locked,
    showNodes,
}: PropsWithChildren<{
    disabled?: boolean;
    expanded?: boolean;
    onToggle: (value: VariableContextType | null) => void;
    value: VariableContextType;
    direction: Direction;
    locked?: boolean;
    showNodes?: boolean;
}>) {
    const dispatch = useDispatch();
    const accordionRef = useRef<HTMLDivElement>(null);

    useLayoutEffect(() => {
        if (!expanded || !accordionRef.current) return;
        setTimeout(() => {
            accordionRef.current.scrollIntoView({
                behavior: "smooth",
                block: "nearest",
            });
        }, 500);
    }, [expanded]);

    return (
        <Accordion
            disabled={disabled}
            expanded={expanded}
            onChange={(event, expanded) => {
                const initiator = direction === "input" ? Initiator.inputAccordion : Initiator.outputAccordion;
                if (expanded) {
                    onToggle(value);
                    dispatch(stopLiveData(initiator));
                } else {
                    onToggle(null);
                    dispatch(startLiveData(initiator));
                }
            }}
            slotProps={{
                transition: {
                    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
                    // @ts-ignore
                    unmountOnExit: true,
                },
            }}
            ref={accordionRef}
            // disableGutters
            sx={{
                zoom: 0.75,
            }}
        >
            <AccordionSummary expandIcon={<ExpandMore />} sx={{ overflow: "hidden" }}>
                <ContextTitle reversed={direction === "input"} context={value} locked={locked} showNodes={showNodes} />
            </AccordionSummary>
            <AccordionDetails sx={{ padding: 0 }}>{children}</AccordionDetails>
        </Accordion>
    );
});

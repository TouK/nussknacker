import { ExpandMore } from "@mui/icons-material";
import { Accordion, AccordionDetails, AccordionSummary, styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { forwardRef, memo, useLayoutEffect, useRef } from "react";
import { useMergeRefs } from "rooks";

import { Initiator, startLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import { useAppDispatch } from "../../../../store/storeHelpers";
import { ContextTitle } from "./ContextTitle";
import type { Direction, VariableContextType } from "./VariableContextTree";

type ContextAccordionProps = PropsWithChildren<{
    disabled?: boolean;
    expanded?: boolean;
    onToggle: (value: VariableContextType | null) => void;
    value: VariableContextType;
    direction: Direction;
    locked?: boolean;
    showNodes?: boolean;
    className?: string;
}>;

const ContextAccordionComponent = memo(
    forwardRef<unknown, ContextAccordionProps>(function ContextAccordion(
        { disabled, expanded, onToggle, children, value, direction, locked, showNodes, className },
        forwardedRef,
    ) {
        const dispatch = useAppDispatch();
        const accordionRef = useRef<HTMLDivElement>(null);

        const ref = useMergeRefs(forwardedRef, accordionRef);
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
                ref={ref}
                className={className}
            >
                <AccordionSummary
                    expandIcon={<ExpandMore />}
                    sx={{
                        overflow: "hidden",
                        zoom: 0.75,
                    }}
                >
                    <ContextTitle reversed={direction === "input"} context={value} locked={locked} showNodes={showNodes} />
                </AccordionSummary>
                <AccordionDetails
                    sx={{
                        padding: 0,
                        zoom: 0.75,
                    }}
                >
                    {children}
                </AccordionDetails>
            </Accordion>
        );
    }),
);

export const ContextAccordion = styled(ContextAccordionComponent)({});

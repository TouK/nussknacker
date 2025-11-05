import type { SlideProps } from "@mui/material";
import { Slide } from "@mui/material";
import React from "react";
import { Transition } from "react-transition-group";
import type { TransitionProps } from "react-transition-group/Transition";

interface DoubleSlideProps extends Omit<SlideProps, "in" | "direction"> {
    in?: boolean;
    directionIn?: SlideProps["direction"];
    directionOut?: SlideProps["direction"];
    timeout?: TransitionProps["timeout"];
    children: React.ReactElement;
}

export const DoubleSlide: React.FC<DoubleSlideProps> = ({
    in: inProp,
    directionIn = "down",
    directionOut = "up",
    timeout = 300,
    children,
    ...rest
}) => (
    <Transition in={inProp} timeout={timeout}>
        {(state) => (
            <Slide
                {...rest}
                in={state === "entering" || state === "entered"}
                direction={state === "exiting" || state === "exited" ? directionOut : directionIn}
                timeout={timeout}
            >
                {children}
            </Slide>
        )}
    </Transition>
);

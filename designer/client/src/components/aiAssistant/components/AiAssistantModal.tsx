import type { ThreadConfig } from "@assistant-ui/react-ui";
import { Thread } from "@assistant-ui/react-ui"; // TODO: "This repo is not actively maintained, these are legacy components and are not up to date."
import { styled } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import type { PropsWithChildren } from "react";
import React, { useCallback, useMemo, useRef } from "react";
import { useMergeRefs, useResizeObserverRef } from "rooks";

import { WindowContent } from "../../../windowManager/WindowContent";
import { AssistantMessage } from "./AssistantMessage";
import { Composer } from "./Composer";
import { ScrollToBottomButton } from "./ScrollToBottomButton";
import { ThreadSuggestions } from "./ThreadWelcome";
import { UserMessage } from "./UserMessage";

const StyledAiAssistant = styled("div")(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    margin: theme.spacing(2),
    paddingBottom: "10%",
    ".aui-root": {
        height: "100%",
    },
    ".aui-thread-viewport": {
        height: "100%",
        display: "flex",
        flexDirection: "column",
    },
    ".aui-thread-viewport-footer": {
        marginTop: "auto",
    },
    ".aui-thread-scroll-to-bottom": {
        display: "none",
    },
}));

function CustomizedThread({ children }: PropsWithChildren) {
    const elRef = useRef<HTMLElement>(null);
    const lastHeight = useRef<number>(0);
    const onResize = useCallback(() => {
        if (!elRef.current) return;
        const diff = elRef.current.clientHeight - lastHeight.current;
        if (diff <= 20) return;
        elRef.current.scrollIntoView({ block: "end", behavior: diff < 300 ? "smooth" : "auto" });
        lastHeight.current = elRef.current.clientHeight;
    }, []);
    const [resizeRef] = useResizeObserverRef(onResize);

    const ref = useMergeRefs(elRef, resizeRef);

    return (
        <StyledAiAssistant ref={ref}>
            <Thread
                assistantAvatar={useMemo<ThreadConfig["assistantAvatar"]>(() => ({ src: undefined }), [])}
                components={useMemo<ThreadConfig["components"]>(
                    () => ({
                        UserMessage,
                        Composer: () => null,
                        AssistantMessage,
                        ThreadWelcome: ThreadSuggestions,
                    }),
                    [],
                )}
            />
            {children}
        </StyledAiAssistant>
    );
}

const AiAssistantModal = ({ ...props }: WindowContentProps) => {
    const components = useMemo(() => ({ Footer: () => <Composer /> }), []);
    return (
        <WindowContent {...props} components={components} closeWithEsc>
            <CustomizedThread>
                <ScrollToBottomButton />
            </CustomizedThread>
        </WindowContent>
    );
};

export default AiAssistantModal;

import { useComposerRuntime, useThreadViewport } from "@assistant-ui/react";
import { styled } from "@mui/material";
import React, { useCallback, useEffect } from "react";

import { LoadingButton } from "../../../windowManager/LoadingButton";
import { NodeInput } from "../../FormElements";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { nodeInput, nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";

const StyledRoot = styled("div")(({ theme }) => ({
    display: "flex",
    flexDirection: "row",
    alignItems: "center",
    margin: theme.spacing(0, 2),
    "& .MuiLoadingButton-root": {
        marginRight: 0,
    },
}));

export const Composer = () => {
    const { send, setText } = useComposerRuntime();
    const { scrollToBottom, onScrollToBottom } = useThreadViewport();

    const [message, setMessage] = React.useState("");

    const handleChange = useCallback(
        (event) => {
            setMessage(event.target.value);
            setText(event.target.value);
        },
        [setText],
    );

    const handleSend = useCallback(() => {
        scrollToBottom();
        send();
        setMessage("");
    }, [scrollToBottom, send]);

    const handleKeyDownOnInput = (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            handleSend();
        }
    };

    useEffect(() => {
        onScrollToBottom(() => {
            const scrollContainer = document.querySelector(`[data-testid="window"] section`);

            if (
                scrollContainer &&
                !document.getElementById("bottom-spacer") &&
                scrollContainer.scrollHeight > scrollContainer.clientHeight
            ) {
                const spacer = document.createElement("div");
                spacer.id = "bottom-spacer";
                spacer.style.height = `${scrollContainer.clientHeight}px`;
                spacer.style.width = "100%";
                scrollContainer.appendChild(spacer);
            }

            if (scrollContainer) {
                scrollContainer.scrollTo({
                    top: scrollContainer.scrollHeight - scrollContainer.clientHeight,
                    behavior: "smooth",
                });

                console.log("scrollContainer.scrollHeight", scrollContainer.scrollHeight);
                console.log("scrollContainer.clientHeight", scrollContainer.clientHeight);
                // Insert the bottom spacer if it doesn't exist
            }
        });
    }, [onScrollToBottom]);

    return (
        <StyledRoot>
            <NodeTable sx={{ margin: 0, width: "90%" }}>
                <div className={nodeValue}>
                    <NodeInput
                        autoFocus
                        autoComplete={"off"}
                        name={"message"}
                        placeholder={"Message AI Assistant"}
                        value={message}
                        className={nodeInput}
                        onChange={handleChange}
                        onKeyDown={handleKeyDownOnInput}
                    />
                </div>
            </NodeTable>
            <LoadingButton disabled={!message} title={"Send"} action={handleSend} />
        </StyledRoot>
    );
};

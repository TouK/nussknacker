import React, { useCallback } from "react";
import { useComposerRuntime } from "@assistant-ui/react";
import { Box } from "@mui/material";
import { LoadingButton } from "../../../windowManager/LoadingButton";
import { NodeInput } from "../../FormElements";
import { nodeInput, nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";

export const Composer = () => {
    const { send, setText } = useComposerRuntime();
    const [message, setMessage] = React.useState("");

    const handleChange = useCallback(
        (event) => {
            setMessage(event.target.value);
            setText(event.target.value);
        },
        [setText],
    );

    const handleSend = useCallback(() => {
        send();
        setMessage("");
    }, [send]);

    return (
        <Box display="flex" flexDirection="row" alignItems="center">
            <NodeTable sx={{ margin: 0, width: "80%" }}>
                <div className={nodeValue}>
                    <NodeInput
                        name={"message"}
                        placeholder={"Message AI Assistant"}
                        value={message}
                        className={nodeInput}
                        onChange={handleChange}
                    />
                </div>
            </NodeTable>
            <LoadingButton disabled={!message} title={"Send"} action={handleSend} />
        </Box>
    );
};

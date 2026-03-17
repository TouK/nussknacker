import type { WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useState } from "react";

import { WindowContent } from "../../windowManager/WindowContent";
import Input from "../graph/node-modal/editors/field/Input";
import { NodeRow } from "../graph/node-modal/node/NodeRow";
import { NodeValue } from "../graph/node-modal/node/NodeValue";
import { useDialogActions } from "../graph/node-modal/node/useDialogActions";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";

const SaveAsTestCaseDialog = (props: WindowContentProps) => {
    const [testCaseName, setTestCaseName] = useState("");

    const { cancel, apply } = useDialogActions({
        onClose: props.close,
        onApply: () => {
            return Promise.resolve();
        },
    });

    const handleNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        setTestCaseName(e.target.value);
    }, []);

    return (
        <WindowContent {...props} buttons={[cancel, apply]}>
            <NodeTable>
                <NodeRow label={"Name"}>
                    <NodeValue>
                        <Input value={testCaseName} onChange={handleNameChange} />
                    </NodeValue>
                </NodeRow>
            </NodeTable>
        </WindowContent>
    );
};

export default SaveAsTestCaseDialog;

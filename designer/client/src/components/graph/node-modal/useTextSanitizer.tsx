import { useCallback, useState } from "react";
import { useTimeoutWhen } from "rooks";

const sanitizeText = (text: string, readable?: boolean) => {
    return !readable && text?.length > 0 ? [...text].fill("•").join("") : text;
};

export const useTextSanitizer = (timeout = 10000) => {
    const [visible, setVisible] = useState(false);
    const sanitize = useCallback((text: string) => sanitizeText(text, visible), [visible]);
    useTimeoutWhen(() => setVisible(false), timeout, visible);
    return {
        visible,
        setVisible,
        sanitize,
    };
};

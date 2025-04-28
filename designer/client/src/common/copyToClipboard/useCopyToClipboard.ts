import copy from "copy-to-clipboard";
import { useEffect, useState } from "react";

export function useCopyClipboard(): [boolean, (value: string) => void] {
    const [isCopied, setIsCopied] = useState<boolean>();
    const [text, setText] = useState<string>();

    useEffect(() => {
        if (isCopied) {
            const id = setTimeout(() => {
                setIsCopied(false);
            }, 1000);

            return () => {
                clearTimeout(id);
            };
        }
    }, [isCopied, text]);

    return [
        isCopied,
        (value: string) => {
            setText(value);
            setIsCopied(copy(value));
        },
    ];
}

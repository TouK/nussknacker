import { useEffect, useState } from "react";

interface Props {
    inputTyping: boolean;
    errorsLength: number;
    action: () => void;
}

export const useDelayedEnterAction = ({ inputTyping, errorsLength, action }: Props) => {
    const [isEnterPressed, setIsEnterPressed] = useState(false);

    useEffect(() => {
        if (isEnterPressed && !inputTyping && errorsLength === 0) {
            action();
            setIsEnterPressed(false);
        }
    }, [errorsLength, inputTyping, isEnterPressed, action]);

    return { setIsEnterPressed };
};

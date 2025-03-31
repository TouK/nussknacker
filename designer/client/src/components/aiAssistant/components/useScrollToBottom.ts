import { useThreadViewport } from "@assistant-ui/react";
import { useEffect } from "react";

export const UseScrollToBottom = () => {
    const { scrollToBottom, onScrollToBottom } = useThreadViewport();

    useEffect(() => {
        const unsubscribe = onScrollToBottom(() => {
            const scrollContainer = document.querySelector(`[data-testid="window"] section`);

            if (scrollContainer) {
                scrollContainer.scrollTo({
                    top: scrollContainer.scrollHeight - scrollContainer.clientHeight,
                    behavior: "smooth",
                });
            }
        });

        return () => unsubscribe();
    }, [onScrollToBottom]);

    const provideBottomSpacer = () => {
        const scrollContainer = document.querySelector(`[data-testid="window"] section`);
        if (scrollContainer && !document.getElementById("bottom-spacer") && scrollContainer.scrollHeight > scrollContainer.clientHeight) {
            const spacer = document.createElement("div");
            spacer.id = "bottom-spacer";
            spacer.style.height = `${scrollContainer.clientHeight}px`;
            spacer.style.width = "100%";
            scrollContainer.appendChild(spacer);
        }
    };

    return { scrollToBottom, provideBottomSpacer };
};

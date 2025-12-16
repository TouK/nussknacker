import { alpha } from "@mui/material";

export type DataEvent = {
    id: string;
    timestamp: number;
    sourceNodeId: string;
    destinationNodeId: string;
};

function animationsCleanup(el: HTMLElement | SVGElement, name: string, stop?: boolean) {
    const animations = el.getAnimations().filter(({ id }) => id.startsWith(name));
    const animation = animations.pop();

    animations.forEach((animation) => {
        if (animation.playState === "running") return;
        animation.cancel();
    });

    if (stop) {
        if (animation?.effect.getComputedTiming().iterations >= Infinity) {
            animation.cancel();
        }
    }
    return animation;
}

export function updateAnimation({
    name,
    throughput = 0,
    events = [],
    el,
}: {
    name: string;
    throughput?: number;
    events?: DataEvent[];
    el: HTMLElement | SVGElement;
}) {
    const currentAnimation = animationsCleanup(el, name, events.length <= 0);
    if (events.length <= 0) return;

    switch (name) {
        case "node": {
            const color = "rgb(255,150,255)";
            const fill = alpha(color, 0.1);
            const fillMax = alpha(color, 0.22);
            if (throughput <= 15) {
                currentAnimation?.cancel();
                el.animate(
                    [
                        { offset: 0, fill: "transparent" },
                        { offset: 0.25, fill: fillMax },
                        { offset: 1, fill: "transparent" },
                    ],
                    {
                        id: name,
                        iterations: events.length,
                        duration: 1000 / events.length,
                        easing: "ease-in-out",
                    },
                );
                return;
            }
            const playbackRate = Math.min(8, throughput) / 10;
            if (currentAnimation) {
                currentAnimation.updatePlaybackRate(playbackRate);
                return;
            }
            el.animate([{ fill }, { fill: fillMax }, { fill }], {
                id: name,
                iterations: Infinity,
                duration: 1000,
                playbackRate,
                fill: "both",
            });
            return;
        }
        case "transition": {
            const offset = 60;
            if (throughput <= 15) {
                currentAnimation?.cancel();
                el.animate([{ strokeDashoffset: 0 }, { strokeDashoffset: offset }], {
                    id: name,
                    iterations: events.length,
                    duration: 1000 / events.length,
                    direction: "reverse",
                    fill: "both",
                });
                return;
            }
            const playbackRate = Math.min(10, throughput);
            if (currentAnimation) {
                currentAnimation.updatePlaybackRate(playbackRate);
                return;
            }

            el.animate([{ strokeDashoffset: 0 }, { strokeDashoffset: offset }], {
                id: name,
                iterations: Infinity,
                duration: 1000,
                direction: "reverse",
                playbackRate,
                fill: "both",
                easing: "cubic-bezier(0.37, 0, 0.63, 1)",
            });
            return;
        }
    }
}

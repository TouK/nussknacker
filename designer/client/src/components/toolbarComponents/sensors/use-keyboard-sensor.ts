import { invariant } from "@hello-pangea/dnd/src/view/use-sensor-marshal/sensors/../../../invariant";
import type {
    DraggableId,
    PreDragActions,
    SensorAPI,
    SnapDragActions,
} from "@hello-pangea/dnd/src/view/use-sensor-marshal/sensors/../../../types";
import bindEvents from "@hello-pangea/dnd/src/view/use-sensor-marshal/sensors/../../event-bindings/bind-events";
import type {
    AnyEventBinding,
    EventOptions,
    KeyboardEventBinding,
} from "@hello-pangea/dnd/src/view/use-sensor-marshal/sensors/../../event-bindings/event-types";
// eslint-disable-next-line no-restricted-syntax
import * as keyCodes from "@hello-pangea/dnd/src/view/use-sensor-marshal/sensors/../../key-codes";
import useLayoutEffect from "@hello-pangea/dnd/src/view/use-sensor-marshal/sensors/../../use-isomorphic-layout-effect";
import preventStandardKeyEvents from "@hello-pangea/dnd/src/view/use-sensor-marshal/sensors/util/prevent-standard-key-events";
import supportedPageVisibilityEventName from "@hello-pangea/dnd/src/view/use-sensor-marshal/sensors/util/supported-page-visibility-event-name";
import { useRef } from "react";
import { useCallback, useMemo } from "use-memo-one";

import type { DelayPromise } from "./types";

// eslint-disable-next-line @typescript-eslint/no-empty-function
function noop() {}

interface KeyMap {
    [key: number]: true;
}

const scrollJumpKeys: KeyMap = {
    [keyCodes.pageDown]: true,
    [keyCodes.pageUp]: true,
    [keyCodes.home]: true,
    [keyCodes.end]: true,
};

function getDraggingBindings(actions: SnapDragActions, stop: () => void): AnyEventBinding[] {
    function cancel() {
        stop();
        actions.cancel();
    }

    function drop() {
        stop();
        actions.drop();
    }

    return [
        {
            eventName: "keydown",
            fn: (event: KeyboardEvent) => {
                if (event.keyCode === keyCodes.escape) {
                    event.preventDefault();
                    cancel();
                    return;
                }

                // Dropping
                if (event.keyCode === keyCodes.space) {
                    // need to stop parent Draggable's thinking this is a lift
                    event.preventDefault();
                    drop();
                    return;
                }

                // Movement

                if (event.keyCode === keyCodes.arrowDown) {
                    event.preventDefault();
                    actions.moveDown();
                    return;
                }

                if (event.keyCode === keyCodes.arrowUp) {
                    event.preventDefault();
                    actions.moveUp();
                    return;
                }

                if (event.keyCode === keyCodes.arrowRight) {
                    event.preventDefault();
                    actions.moveRight();
                    return;
                }

                if (event.keyCode === keyCodes.arrowLeft) {
                    event.preventDefault();
                    actions.moveLeft();
                    return;
                }

                // preventing scroll jumping at this time
                if (scrollJumpKeys[event.keyCode]) {
                    event.preventDefault();
                    return;
                }

                preventStandardKeyEvents(event);
            },
        },
        // any mouse actions kills a drag
        {
            eventName: "mousedown",
            fn: cancel,
        },
        {
            eventName: "mouseup",
            fn: cancel,
        },
        {
            eventName: "click",
            fn: cancel,
        },
        {
            eventName: "touchstart",
            fn: cancel,
        },
        // resizing the browser kills a drag
        {
            eventName: "resize",
            fn: cancel,
        },
        // kill if the user is using the mouse wheel
        // We are not supporting wheel / trackpad scrolling with keyboard dragging
        {
            eventName: "wheel",
            fn: cancel,
            // chrome says it is a violation for this to not be passive
            // it is fine for it to be passive as we just cancel as soon as we get
            // any event
            options: { passive: true },
        },
        // Cancel on page visibility change
        {
            eventName: supportedPageVisibilityEventName,
            fn: cancel,
        },
    ];
}

// Original @hello-pangea/dnd sensor with delay
export default function useKeyboardSensor(api: SensorAPI, delayPromiseGetter: (draggableId: DraggableId) => DelayPromise) {
    const delayPromise = useRef<DelayPromise>(null);
    const unbindEventsRef = useRef<() => void>(noop);

    const startCaptureBinding: KeyboardEventBinding = useMemo(
        () => ({
            eventName: "keydown",
            fn: async function onKeyDown(event: KeyboardEvent) {
                // Event already used
                if (event.defaultPrevented) {
                    return;
                }

                // Need to start drag with a spacebar press
                if (event.keyCode !== keyCodes.space) {
                    return;
                }

                const draggableId: DraggableId | null = api.findClosestDraggableId(event);

                if (!draggableId) {
                    return;
                }

                delayPromise.current = delayPromiseGetter(draggableId);
                await delayPromise.current;

                const preDrag: PreDragActions | null = api.tryGetLock(
                    draggableId,
                    // abort function not defined yet
                    // eslint-disable-next-line @typescript-eslint/no-use-before-define
                    stop,
                    { sourceEvent: event },
                );

                // Cannot start capturing at this time
                if (!preDrag) {
                    return;
                }

                // we are consuming the event
                event.preventDefault();
                let isCapturing = true;

                // There is no pending period for a keyboard drag
                // We can lift immediately
                await new Promise<void>((resolve) => {
                    requestAnimationFrame(() => resolve());
                });
                const actions: SnapDragActions = preDrag.snapLift();

                // unbind this listener
                unbindEventsRef.current();

                // setup our function to end everything
                function stop() {
                    invariant(isCapturing, "Cannot stop capturing a keyboard drag when not capturing");
                    isCapturing = false;

                    delayPromise.current?.then(({ end }) => end.resolve());

                    // unbind dragging bindings
                    unbindEventsRef.current();
                    // start listening for capture again
                    // eslint-disable-next-line @typescript-eslint/no-use-before-define
                    listenForCapture();
                }

                // bind dragging listeners
                unbindEventsRef.current = bindEvents(window, getDraggingBindings(actions, stop), { capture: true, passive: false });
            },
        }),
        // not including startPendingDrag as it is not defined initially
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [api],
    );

    const listenForCapture = useCallback(
        function tryStartCapture() {
            const options: EventOptions = {
                passive: false,
                capture: true,
            };

            unbindEventsRef.current = bindEvents(window, [startCaptureBinding], options);
        },
        [startCaptureBinding],
    );

    useLayoutEffect(
        function mount() {
            listenForCapture();

            // kill any pending window events when unmounting
            return function unmount() {
                unbindEventsRef.current();
            };
        },
        [listenForCapture],
    );
}

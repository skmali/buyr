// Shared motion layer for the catalog page: an interruptible spring, swipe-to-dismiss toasts,
// modals that grow out of the control that opened them, and the header's scroll-edge state.
// Everything animates from the current on-screen value, so any motion can be grabbed or
// reversed mid-flight.

(function () {
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

    // ---- Spring ----
    // damping 1 = critically damped (no overshoot); response = seconds to (roughly) reach the target.
    // Velocity is in units/second so a gesture's release velocity can be handed straight in.
    function spring({ from, to, velocity = 0, damping = 1, response = 0.35, onUpdate, onComplete }) {
        const stiffness = Math.pow((2 * Math.PI) / response, 2);
        const friction = (4 * Math.PI * damping) / response;
        let value = from;
        let v = velocity;
        let last = performance.now();
        let frame = requestAnimationFrame(step);

        function step(now) {
            let dt = Math.min((now - last) / 1000, 0.064);
            last = now;
            // Fixed small sub-steps keep the integration stable when a frame runs long
            while (dt > 0) {
                const h = Math.min(dt, 0.004);
                v += (-stiffness * (value - to) - friction * v) * h;
                value += v * h;
                dt -= h;
            }
            if (Math.abs(v) < 1 && Math.abs(value - to) < 0.5) {
                onUpdate(to);
                if (onComplete) onComplete();
                return;
            }
            onUpdate(value);
            frame = requestAnimationFrame(step);
        }

        return { stop: () => cancelAnimationFrame(frame) };
    }

    // Where a flick would come to rest (Apple's scroll-deceleration projection)
    function project(velocity, decelerationRate = 0.998) {
        return ((velocity / 1000) * decelerationRate) / (1 - decelerationRate);
    }

    // Progressive resistance past a boundary instead of a hard stop
    function rubberband(overshoot, dimension, constant = 0.55) {
        return (overshoot * dimension * constant) / (dimension + constant * Math.abs(overshoot));
    }

    // ---- Toasts: slide in from the right edge, swipe (or close) back out the same way ----

    const TOAST_LIFETIME_MS = 6000;
    const DRAG_THRESHOLD_PX = 8;

    function setupToast(toast) {
        if (toast.dataset.motion) {
            return;
        }
        toast.dataset.motion = "on";
        toast.classList.remove("animate-bounce");
        toast.style.touchAction = "pan-y";
        toast.setAttribute("role", "status");

        let x = 0;
        let anim = null;
        let timer = null;
        let dismissed = false;
        let pointer = null; // { id, startX, originX, tracking, samples }

        const offscreenX = () => toast.offsetWidth + 40;

        function render(value) {
            x = value;
            toast.style.transform = `translate3d(${value}px, 0, 0)`;
            toast.style.opacity = String(Math.max(0, 1 - Math.max(0, value) / toast.offsetWidth));
        }

        function schedule() {
            clearTimeout(timer);
            timer = setTimeout(() => dismiss(0), TOAST_LIFETIME_MS);
        }

        function dismiss(velocity) {
            if (dismissed) {
                return;
            }
            dismissed = true;
            clearTimeout(timer);
            if (anim) anim.stop();
            if (reducedMotion.matches) {
                toast.style.transition = "opacity 200ms ease";
                toast.style.opacity = "0";
                setTimeout(() => toast.remove(), 200);
                return;
            }
            anim = spring({ from: x, to: offscreenX(), velocity, response: 0.3, onUpdate: render, onComplete: () => toast.remove() });
        }

        // Enter along the same path it will leave by
        if (reducedMotion.matches) {
            toast.style.opacity = "0";
            requestAnimationFrame(() => {
                toast.style.transition = "opacity 200ms ease";
                toast.style.opacity = "1";
            });
        } else {
            render(offscreenX());
            anim = spring({ from: x, to: 0, response: 0.4, onUpdate: render });
        }
        schedule();

        // The close button leaves the same way a swipe does, instead of vanishing
        const closeBtn = toast.querySelector("button");
        if (closeBtn) {
            closeBtn.removeAttribute("onclick");
            closeBtn.onclick = () => dismiss(0);
            closeBtn.setAttribute("aria-label", "Dismiss notification");
        }

        // Hovering or pressing holds the toast so it can actually be read
        toast.addEventListener("pointerenter", () => clearTimeout(timer));
        toast.addEventListener("pointerleave", () => { if (!pointer && !dismissed) schedule(); });

        toast.addEventListener("pointerdown", (e) => {
            if (dismissed || e.button !== 0 || e.target.closest("button")) {
                return;
            }
            pointer = { id: e.pointerId, startX: e.clientX, originX: x, tracking: false, samples: [] };
            clearTimeout(timer);
        });

        toast.addEventListener("pointermove", (e) => {
            if (!pointer || e.pointerId !== pointer.id) {
                return;
            }
            if (!pointer.tracking) {
                if (Math.abs(e.clientX - pointer.startX) < DRAG_THRESHOLD_PX) {
                    return;
                }
                // Intent is clear: grab it (even mid-flight) from where it is on screen right now
                pointer.tracking = true;
                pointer.startX = e.clientX;
                pointer.originX = x;
                if (anim) anim.stop();
                toast.setPointerCapture(e.pointerId);
            }
            let next = pointer.originX + (e.clientX - pointer.startX);
            if (next < 0) {
                next = rubberband(next, toast.offsetWidth);
            }
            render(next);
            pointer.samples.push({ x: e.clientX, t: e.timeStamp });
            while (pointer.samples.length > 2 && e.timeStamp - pointer.samples[0].t > 100) {
                pointer.samples.shift();
            }
        });

        function release(e) {
            if (!pointer || e.pointerId !== pointer.id) {
                return;
            }
            const { tracking, samples } = pointer;
            pointer = null;
            if (!tracking) {
                schedule();
                return;
            }
            let velocity = 0;
            if (samples.length >= 2) {
                const first = samples[0];
                const lastSample = samples[samples.length - 1];
                const dt = lastSample.t - first.t;
                if (dt > 0) velocity = ((lastSample.x - first.x) / dt) * 1000;
            }
            // Decide from where the flick is going, not where the finger let go
            if (x + project(velocity) > toast.offsetWidth / 2) {
                dismiss(velocity);
            } else {
                anim = spring({ from: x, to: 0, velocity, damping: 0.8, response: 0.3, onUpdate: render });
                schedule();
            }
        }

        toast.addEventListener("pointerup", release);
        toast.addEventListener("pointercancel", release);
    }

    const toastContainer = document.getElementById("toast-container");
    if (toastContainer) {
        Array.from(toastContainer.children).forEach(setupToast);
        new MutationObserver((mutations) => {
            mutations.forEach((m) => m.addedNodes.forEach((node) => {
                if (node.nodeType === Node.ELEMENT_NODE) setupToast(node);
            }));
        }).observe(toastContainer, { childList: true });
    }

    // ---- Modals: materialize out of the control that opened them, retreat back into it ----

    let lastPressed = null;
    document.addEventListener("pointerdown", (e) => {
        lastPressed = e.target.closest("button, a, [role='button']");
    }, true);

    function openModal(id) {
        const modal = document.getElementById(id);
        if (!modal) {
            return;
        }
        const panel = modal.querySelector("[data-modal-panel]");
        const trigger = lastPressed && !modal.contains(lastPressed) && lastPressed.isConnected ? lastPressed : null;

        if (modal.dataset.state !== "open") {
            modal._returnFocus = document.activeElement;
        }
        modal.classList.remove("hidden");

        if (panel) {
            if (trigger) {
                // offsetLeft/Top ignore the panel's own (scaled) transform, so this is the true origin
                const t = trigger.getBoundingClientRect();
                const m = modal.getBoundingClientRect();
                const originX = t.left + t.width / 2 - m.left - panel.offsetLeft;
                const originY = t.top + t.height / 2 - m.top - panel.offsetTop;
                panel.style.transformOrigin = `${originX}px ${originY}px`;
            } else {
                panel.style.transformOrigin = "";
            }
        }

        void modal.offsetWidth; // commit the closed styles so the transition runs from them
        modal.dataset.state = "open";

        if (panel && !panel.contains(document.activeElement)) {
            panel.focus({ preventScroll: true });
        }
    }

    function closeModal(id) {
        const modal = document.getElementById(id);
        if (!modal || modal.dataset.state !== "open") {
            return;
        }
        modal.dataset.state = "closed";
        // Reopening mid-exit just flips the state back; the transition reverses from where it is
        setTimeout(() => {
            if (modal.dataset.state === "closed") {
                modal.classList.add("hidden");
            }
        }, 260);

        const returnTo = modal._returnFocus;
        modal._returnFocus = null;
        if (returnTo && returnTo.isConnected && typeof returnTo.focus === "function") {
            returnTo.focus({ preventScroll: true });
        }
    }

    // ---- Side sheet: slides in from the right edge, can be dragged back out with momentum ----

    const sheets = [];

    function createSheet(id) {
        const root = document.getElementById(id);
        const panel = root && root.querySelector("[data-sheet-panel]");
        const scrim = root && root.querySelector("[data-sheet-scrim]");
        if (!panel) {
            return null;
        }

        let x = 0;
        let anim = null;
        let isOpen = false;
        let returnFocus = null;
        let pointer = null;

        const width = () => panel.offsetWidth || 448;

        function render(value) {
            x = value;
            const progress = Math.min(1, Math.max(0, 1 - value / width()));
            if (reducedMotion.matches) {
                panel.style.transform = "";
                panel.style.opacity = String(progress);
            } else {
                panel.style.transform = `translate3d(${value}px, 0, 0)`;
            }
            if (scrim) scrim.style.opacity = String(progress);
        }

        function settle(target, { velocity = 0, damping = 1, response = 0.35, onComplete } = {}) {
            if (anim) anim.stop();
            anim = spring({ from: x, to: target, velocity, damping, response, onUpdate: render, onComplete });
        }

        function open() {
            if (isOpen) {
                return;
            }
            isOpen = true;
            returnFocus = document.activeElement;
            const wasHidden = root.classList.contains("hidden");
            root.classList.remove("hidden");
            root.dataset.state = "open";
            if (wasHidden) render(width()); // start off-screen; otherwise continue from where it is
            settle(0);
            panel.focus({ preventScroll: true });
        }

        function close(velocity = 0) {
            if (!isOpen) {
                return;
            }
            isOpen = false;
            root.dataset.state = "closed";
            settle(width(), {
                velocity,
                response: 0.3,
                onComplete: () => { if (!isOpen) root.classList.add("hidden"); },
            });
            if (returnFocus && returnFocus.isConnected && typeof returnFocus.focus === "function") {
                returnFocus.focus({ preventScroll: true });
            }
            returnFocus = null;
        }

        if (scrim) {
            scrim.addEventListener("click", () => close(0));
        }

        // Drag from the sheet's header; form fields keep their normal pointer behavior
        const handle = panel.querySelector("[data-sheet-handle]") || panel;
        handle.style.touchAction = "pan-y";

        handle.addEventListener("pointerdown", (e) => {
            if (!isOpen || e.button !== 0 || e.target.closest("button, input, textarea, select, a, label")) {
                return;
            }
            pointer = { id: e.pointerId, startX: e.clientX, originX: x, tracking: false, samples: [] };
        });

        handle.addEventListener("pointermove", (e) => {
            if (!pointer || e.pointerId !== pointer.id) {
                return;
            }
            if (!pointer.tracking) {
                if (Math.abs(e.clientX - pointer.startX) < DRAG_THRESHOLD_PX) {
                    return;
                }
                pointer.tracking = true;
                pointer.startX = e.clientX;
                pointer.originX = x;
                if (anim) anim.stop();
                handle.setPointerCapture(e.pointerId);
            }
            let next = pointer.originX + (e.clientX - pointer.startX);
            if (next < 0) {
                next = rubberband(next, width());
            }
            render(next);
            pointer.samples.push({ x: e.clientX, t: e.timeStamp });
            while (pointer.samples.length > 2 && e.timeStamp - pointer.samples[0].t > 100) {
                pointer.samples.shift();
            }
        });

        function release(e) {
            if (!pointer || e.pointerId !== pointer.id) {
                return;
            }
            const { tracking, samples } = pointer;
            pointer = null;
            if (!tracking) {
                return;
            }
            let velocity = 0;
            if (samples.length >= 2) {
                const first = samples[0];
                const lastSample = samples[samples.length - 1];
                const dt = lastSample.t - first.t;
                if (dt > 0) velocity = ((lastSample.x - first.x) / dt) * 1000;
            }
            if (x + project(velocity) > width() / 2) {
                close(velocity);
            } else {
                // The drag carried momentum, so a little bounce on the way back reads as physical
                settle(0, { velocity, damping: 0.8, response: 0.3 });
            }
        }

        handle.addEventListener("pointerup", release);
        handle.addEventListener("pointercancel", release);

        const controller = { open, close: () => close(0), get isOpen() { return isOpen; } };
        sheets.push(controller);
        return controller;
    }

    function dismissTopModal() {
        const open = document.querySelectorAll("[data-modal][data-state='open']");
        const top = open[open.length - 1];
        if (!top) {
            // No modal above it: Escape closes an open sheet next
            const sheet = sheets.filter((s) => s.isOpen).pop();
            if (sheet) {
                sheet.close();
                return true;
            }
            return false;
        }
        const handler = window[top.dataset.onDismiss];
        if (typeof handler === "function") {
            handler();
        } else {
            closeModal(top.id);
        }
        return true;
    }

    // Wayfinding: Escape or a click on the scrim always gets you out
    document.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && dismissTopModal()) {
            e.preventDefault();
        }
    });

    let scrimPressed = null;
    document.addEventListener("pointerdown", (e) => {
        scrimPressed = e.target.matches && e.target.matches("[data-modal]") ? e.target : null;
    });
    document.addEventListener("click", (e) => {
        if (scrimPressed && e.target === scrimPressed) {
            dismissTopModal();
        }
        scrimPressed = null;
    });

    // ---- Header: show its edge only once content is actually scrolling underneath it ----

    const header = document.getElementById("app-header");
    if (header) {
        const syncHeader = () => header.toggleAttribute("data-scrolled", window.scrollY > 4);
        window.addEventListener("scroll", syncHeader, { passive: true });
        syncHeader();
    }

    // ---- Catalog: the empty state hides as soon as a product lands (deleting the last one shows it again) ----

    document.addEventListener("htmx:afterSwap", (e) => {
        if (e.detail.target && e.detail.target.id === "product-grid-container") {
            const emptyState = document.getElementById("empty-catalog-state");
            if (emptyState) emptyState.classList.add("hidden");
        }
    });

    window.UiMotion = { spring, openModal, closeModal, createSheet, reducedMotion };
})();

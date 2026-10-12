// "New Product" form: image-source toggle, upload preview, and the confirmation
// modal that gates both (a) submitting a new product with an image and
// (b) generating/regenerating an existing product's image. Nothing involving
// an image reaches the backend-as-final until the user has seen it and clicked
// "Use this image".

// ---- Form panel toggle (unchanged behavior from before the confirmation flow) ----

function updateImageSourceUi(source) {
    const uploadPanel = document.getElementById("image-upload-panel");
    const generateHint = document.getElementById("image-generate-hint");
    if (!uploadPanel || !generateHint) {
        return;
    }

    uploadPanel.classList.toggle("hidden", source !== "upload");
    generateHint.classList.toggle("hidden", source !== "generate");

    if (source !== "upload") {
        clearImagePreview();
    }
}

function previewSelectedImage(fileInput) {
    const preview = document.getElementById("image-preview");
    if (!preview) {
        return;
    }

    const file = fileInput.files && fileInput.files[0];
    if (!file) {
        clearImagePreview();
        return;
    }

    const reader = new FileReader();
    reader.onload = (event) => {
        preview.src = event.target.result;
        preview.classList.remove("hidden");
    };
    reader.readAsDataURL(file);
}

function clearImagePreview() {
    const preview = document.getElementById("image-preview");
    if (preview) {
        preview.src = "";
        preview.classList.add("hidden");
    }
}

// Called after the "New Product" form is submitted (success or failure) to
// return the image panel to its default state.
function resetImagePanel() {
    clearImagePreview();
    updateImageSourceUi("none");
    const fileInput = document.getElementById("imageFile");
    if (fileInput) {
        fileInput.value = "";
    }
}

// ---- "Add to Catalog" entry point: gates the real form submission behind confirmation ----

let modalContext = null; // { mode: 'create-upload' | 'create-generate' | 'existing-generate', description, productId, name }

async function handleAddToCatalogClick() {
    const form = document.getElementById("add-product-form");
    if (!form || !form.reportValidity()) {
        return; // native validation (required fields) shows its own UI
    }

    const source = document.querySelector('input[name="imageSource"]:checked').value;

    if (source === "none") {
        dispatchConfirmedSubmit(form);
        return;
    }

    if (source === "upload") {
        const fileInput = document.getElementById("imageFile");
        const file = fileInput.files && fileInput.files[0];
        if (!file) {
            showToast("Choose a photo to upload, or switch to \"No image\".", "error");
            return;
        }
        modalContext = { mode: "create-upload" };
        openImageConfirmModal({
            title: "Confirm Photo",
            hint: "Use this photo for the new product?",
            showRegenerate: false,
            confirmLabel: "Use this image & add product",
        });
        setModalState("ready", { src: document.getElementById("image-preview").src });
        return;
    }

    // source === "generate"
    const description = document.getElementById("description").value.trim();
    if (!description) {
        showToast("Add a description first so an image can be generated from it.", "error");
        document.getElementById("description").focus();
        return;
    }

    modalContext = { mode: "create-generate", description };
    openImageConfirmModal({
        title: "Confirm Generated Image",
        hint: "Generated from your product description. Use it, or try again?",
        showRegenerate: true,
        confirmLabel: "Use this image & add product",
    });
    await runGeneratePreview(description);
}

function dispatchConfirmedSubmit(formEl) {
    formEl.dispatchEvent(new Event("confirmed-submit", { bubbles: true }));
}

// ---- Existing-product "Generate/Regenerate Image" button (on each catalog card) ----

async function regenerateProductImage(buttonEl) {
    const productId = buttonEl.dataset.productId;
    const description = (buttonEl.dataset.description || "").trim();
    const name = buttonEl.dataset.name || "this product";

    if (!description) {
        showToast(`"${name}" has no description to generate an image from.`, "error");
        return;
    }

    modalContext = { mode: "existing-generate", description, productId, name };
    openImageConfirmModal({
        title: `Generate Image for "${name}"`,
        hint: "Generated from this product's description. Use it, or try again?",
        showRegenerate: true,
        confirmLabel: "Use this image",
    });
    await runGeneratePreview(description);
}

// ---- Shared: calling the stateless generate-preview endpoint ----

async function runGeneratePreview(description) {
    setModalState("loading");
    try {
        const response = await fetch("/products/image/generate-preview", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: "description=" + encodeURIComponent(description),
        });

        if (!response.ok) {
            const message = await extractErrorMessage(response);
            setModalState("error", { errorText: message });
            return;
        }

        const blob = await response.blob();
        if (modalContext) {
            modalContext.blob = blob;
        }
        setModalState("ready", { src: URL.createObjectURL(blob) });
    } catch (e) {
        setModalState("error", { errorText: "Could not reach the image generation service." });
    }
}

async function extractErrorMessage(response) {
    try {
        const data = await response.json();
        if (data && data.message) {
            return data.message;
        }
    } catch (e) {
        // response wasn't JSON - fall through to the generic message below
    }
    if (response.status === 503) {
        return "Image generation isn't available right now. Try again later.";
    }
    return "Image generation failed. You can try again or cancel.";
}

// ---- Modal plumbing ----

function openImageConfirmModal({ title, hint, showRegenerate, confirmLabel }) {
    document.getElementById("image-confirm-title").textContent = title;
    document.getElementById("image-confirm-hint").textContent = hint;
    document.getElementById("image-confirm-use-label").textContent = confirmLabel;
    document.getElementById("image-confirm-regenerate-btn").classList.toggle("hidden", !showRegenerate);
    UiMotion.openModal("image-confirm-modal");
}

function closeImageConfirmModal() {
    UiMotion.closeModal("image-confirm-modal");
    setButtonsDisabled(false);
    modalContext = null;
}

// Each state explicitly sets whether Confirm/Regenerate are enabled, rather than tracking a
// separate "busy" flag - this is what keeps "Use this image" from silently re-enabling after a
// failed generation with no actual image behind it.
function setModalState(state, { src, errorText } = {}) {
    const img = document.getElementById("image-confirm-preview");
    const spinner = document.getElementById("image-confirm-spinner");
    const error = document.getElementById("image-confirm-error");

    img.classList.add("hidden");
    spinner.classList.remove("flex");
    spinner.classList.add("hidden");
    error.classList.remove("flex");
    error.classList.add("hidden");

    if (state === "loading") {
        spinner.classList.remove("hidden");
        spinner.classList.add("flex");
        setButtonsDisabled(true);
    } else if (state === "ready") {
        // Cross-fade the image in once it has decoded, rather than popping it in half-painted
        img.style.opacity = "0";
        img.onload = () => { img.style.opacity = ""; };
        img.src = src;
        if (img.complete && img.naturalWidth) {
            img.style.opacity = ""; // already decoded (same image again) - load may not fire
        }
        img.classList.remove("hidden");
        setButtonsDisabled(false);
    } else if (state === "error") {
        document.getElementById("image-confirm-error-text").textContent = errorText || "Something went wrong.";
        error.classList.remove("hidden");
        error.classList.add("flex");
        // Allow retrying (Regenerate), but there's no valid image yet, so Confirm must stay disabled.
        document.getElementById("image-confirm-regenerate-btn").disabled = false;
        document.getElementById("image-confirm-regenerate-btn").classList.remove("opacity-60");
        document.getElementById("image-confirm-use-btn").disabled = true;
        document.getElementById("image-confirm-use-btn").classList.add("opacity-60");
    }
}

function setButtonsDisabled(isDisabled) {
    const confirmBtn = document.getElementById("image-confirm-use-btn");
    const regenBtn = document.getElementById("image-confirm-regenerate-btn");
    confirmBtn.disabled = isDisabled;
    regenBtn.disabled = isDisabled;
    confirmBtn.classList.toggle("opacity-60", isDisabled);
    regenBtn.classList.toggle("opacity-60", isDisabled);
}

async function handleRegenerateClick() {
    if (!modalContext || !modalContext.description) {
        return;
    }
    await runGeneratePreview(modalContext.description);
}

async function handleConfirmImageClick() {
    if (!modalContext) {
        return;
    }

    if (modalContext.mode === "create-upload") {
        closeImageConfirmModal();
        dispatchConfirmedSubmit(document.getElementById("add-product-form"));
        return;
    }

    if (modalContext.mode === "create-generate") {
        if (!modalContext.blob) {
            return; // still loading/errored - button should be disabled, but guard anyway
        }
        const file = new File([modalContext.blob], "generated-image.png", { type: modalContext.blob.type || "image/png" });
        const dataTransfer = new DataTransfer();
        dataTransfer.items.add(file);
        document.getElementById("imageFile").files = dataTransfer.files;

        closeImageConfirmModal();
        dispatchConfirmedSubmit(document.getElementById("add-product-form"));
        return;
    }

    if (modalContext.mode === "existing-generate") {
        if (!modalContext.blob) {
            return;
        }
        setButtonsDisabled(true);
        try {
            const formData = new FormData();
            formData.append("file", modalContext.blob, "generated-image.png");
            formData.append("source", "GENERATED");

            const response = await fetch(`/products/${modalContext.productId}/image?source=GENERATED`, {
                method: "POST",
                body: formData,
            });

            if (!response.ok) {
                const message = await extractErrorMessage(response);
                setModalState("error", { errorText: message });
                return;
            }

            const img = document.getElementById("product-image-" + modalContext.productId);
            if (img) {
                img.src = `/products/${modalContext.productId}/image?t=` + Date.now();
            }
            showToast(`Image updated for "${modalContext.name}".`, "success");
            closeImageConfirmModal();
        } catch (e) {
            setModalState("error", { errorText: "Could not save the image. Try again." });
        }
    }
}

// ---- Toasts ----

function showToast(message, variant) {
    const container = document.getElementById("toast-container");
    if (!container) {
        return;
    }

    const isError = variant !== "success";
    const toast = document.createElement("div");
    toast.className = (isError
        ? "bg-rose-50 border border-rose-200 text-rose-800"
        : "bg-emerald-50 border border-emerald-200 text-emerald-800")
        + " p-4 rounded-xl shadow-lg flex items-center space-x-3 max-w-sm";

    const icon = document.createElement("i");
    icon.className = (isError ? "fa-solid fa-triangle-exclamation text-rose-500" : "fa-solid fa-circle-check text-emerald-500") + " text-lg";

    const textWrap = document.createElement("div");
    textWrap.className = "flex-1";
    const text = document.createElement("p");
    text.className = "text-xs font-semibold";
    text.textContent = message;
    textWrap.appendChild(text);

    const closeBtn = document.createElement("button");
    closeBtn.type = "button";
    closeBtn.className = (isError ? "text-rose-400 hover:text-rose-600" : "text-emerald-400 hover:text-emerald-600") + " transition";
    closeBtn.innerHTML = '<i class="fa-solid fa-xmark"></i>';

    // Entrance, auto-dismiss, swipe-to-dismiss and the close button are handled by ui-motion.js
    toast.append(icon, textWrap, closeBtn);
    container.appendChild(toast);
}

// Toggles the "New Product" form's image panels (upload file picker vs. the
// "generate from description" hint) based on the selected imageSource radio,
// and shows a client-side preview of a selected upload before submit.

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
// return the image panel to its default state, since this.reset() on the
// <form> clears the radio/file inputs but not the preview <img> or the
// now-stale hidden/visible panel classes.
function resetImagePanel() {
    clearImagePreview();
    updateImageSourceUi("none");
}

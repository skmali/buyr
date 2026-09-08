let activeScannerTab = "barcode";
let html5QrScanner = null;
let isQuaggaRunning = false;
let scanLocked = false;

const SCANNER_TAB_ACTIVE_CLASSES = ["border-indigo-600", "text-indigo-600", "bg-white", "shadow-sm", "border", "border-slate-100"];
const SCANNER_TAB_INACTIVE_CLASSES = ["border-transparent", "text-slate-500"];
const BARCODE_READERS = ["ean_reader", "ean_8_reader", "code_128_reader", "upc_reader", "upc_e_reader", "code_39_reader"];
const SCANNER_TABS = ["barcode", "qrcode", "usb"];

function smoothScrollToCatalog() {
    const anchor = document.getElementById("catalog-section-anchor");
    if (anchor) {
        anchor.scrollIntoView({ behavior: "smooth" });
    }
}

function filterProducts() {
    const query = document.getElementById("product-search-input").value.toLowerCase();
    filterCatalogWithQuery(query);
}

function filterCatalogWithQuery(query) {
    const cards = document.querySelectorAll("#product-grid-container > div");
    let foundAny = false;

    cards.forEach(card => {
        const titleElement = card.querySelector("h3");
        const descElement = card.querySelector("p");
        const title = titleElement ? titleElement.textContent.toLowerCase() : "";
        const desc = descElement ? descElement.textContent.toLowerCase() : "";

        if (title.includes(query) || desc.includes(query)) {
            card.style.display = "";
            foundAny = true;
        } else {
            card.style.display = "none";
        }
    });

    const emptyState = document.getElementById("empty-catalog-state");
    if (emptyState) {
        if (foundAny) {
            emptyState.style.display = "none";
        } else {
            emptyState.style.display = "block";
        }
    }
}

function openScannerModal() {
    document.getElementById("scanner-modal").classList.remove("hidden");
    scanLocked = false;
    setScannerStatus("Ready to scan...");
    startActiveScanner();
}

function closeScannerModal() {
    stopAllScanners();
    document.getElementById("scanner-modal").classList.add("hidden");
}

function switchScannerTab(tab) {
    if (tab === activeScannerTab) {
        return;
    }
    stopAllScanners();
    activeScannerTab = tab;
    scanLocked = false;

    SCANNER_TABS.forEach((t) => {
        const btn = document.getElementById(`tab-${t}`);
        const panel = document.getElementById(`panel-${t}`);
        if (!btn || !panel) {
            return;
        }
        if (t === tab) {
            btn.classList.add(...SCANNER_TAB_ACTIVE_CLASSES);
            btn.classList.remove(...SCANNER_TAB_INACTIVE_CLASSES);
            panel.classList.remove("hidden");
        } else {
            btn.classList.remove(...SCANNER_TAB_ACTIVE_CLASSES);
            btn.classList.add(...SCANNER_TAB_INACTIVE_CLASSES);
            panel.classList.add("hidden");
        }
    });

    setScannerStatus("Ready to scan...");
    startActiveScanner();
}

function startActiveScanner() {
    if (activeScannerTab === "barcode") {
        startBarcodeScanner();
    } else if (activeScannerTab === "qrcode") {
        startQrScanner();
    } else if (activeScannerTab === "usb") {
        startUsbScanner();
    }
}

function stopAllScanners() {
    stopBarcodeScanner();
    stopQrScanner();
    stopUsbScanner();
}

function startBarcodeScanner() {
    if (isQuaggaRunning || typeof Quagga === "undefined") {
        return;
    }

    Quagga.init({
        inputStream: {
            type: "LiveStream",
            target: document.getElementById("barcode-interactive"),
            constraints: { facingMode: "environment" }
        },
        locate: true,
        decoder: { readers: BARCODE_READERS }
    }, (err) => {
        if (err) {
            console.error("Failed to start barcode scanner", err);
            setScannerStatus("Camera unavailable");
            return;
        }
        Quagga.start();
        isQuaggaRunning = true;
    });

    Quagga.offDetected(onBarcodeDetected);
    Quagga.onDetected(onBarcodeDetected);
}

function onBarcodeDetected(result) {
    const code = result && result.codeResult ? result.codeResult.code : null;
    if (code) {
        handleScanResult(code);
    }
}

function stopBarcodeScanner() {
    if (isQuaggaRunning && typeof Quagga !== "undefined") {
        Quagga.offDetected(onBarcodeDetected);
        Quagga.stop();
        isQuaggaRunning = false;
    }
}

function startQrScanner() {
    if (html5QrScanner || typeof Html5QrcodeScanner === "undefined") {
        return;
    }

    html5QrScanner = new Html5QrcodeScanner("qr-reader", { fps: 10, qrbox: 220 }, false);
    html5QrScanner.render(onQrDetected, () => {});
}

function onQrDetected(decodedText) {
    handleScanResult(decodedText);
}

function stopQrScanner() {
    if (html5QrScanner) {
        const scannerToClear = html5QrScanner;
        html5QrScanner = null;
        scannerToClear.clear().catch(() => {});
    }
}

function startUsbScanner() {
    const input = document.getElementById("usb-scan-input");
    if (input) {
        input.value = "";
        input.focus();
    }
}

function stopUsbScanner() {
    const input = document.getElementById("usb-scan-input");
    if (input) {
        input.value = "";
        input.blur();
    }
}

function handleUsbScanKeydown(event) {
    if (event.key !== "Enter") {
        return;
    }
    event.preventDefault();
    const input = event.target;
    const code = input.value.trim();
    input.value = "";
    if (code) {
        handleUsbScanResult(code);
    }
}

// USB scanners are fast, keyboard-emulating HID devices - unlike a single camera detection,
// batch-scanning several codes in a row is the whole point, so this keeps the modal open
// (except when autofilling, where focus needs to move to the product form) instead of
// closing after every scan like handleScanResult does for the camera tabs.
function handleUsbScanResult(code) {
    setScannerStatus(code);
    const selectedAction = document.querySelector('input[name="scan-action"]:checked');
    const action = selectedAction ? selectedAction.value : "search";

    if (action === "autofill") {
        applyScannedCodeToForm(code).finally(closeScannerModal);
    } else {
        applyScannedCodeToSearch(code);
        const input = document.getElementById("usb-scan-input");
        if (input) {
            input.focus();
        }
    }
}

function handleScanResult(code) {
    if (scanLocked) {
        return;
    }
    scanLocked = true;

    setScannerStatus(code);
    const selectedAction = document.querySelector('input[name="scan-action"]:checked');
    const action = selectedAction ? selectedAction.value : "search";

    if (action === "autofill") {
        applyScannedCodeToForm(code).finally(closeScannerModal);
    } else {
        applyScannedCodeToSearch(code);
        closeScannerModal();
    }
}

function applyScannedCodeToSearch(code) {
    const input = document.getElementById("product-search-input");
    input.value = code;
    filterCatalogWithQuery(code.toLowerCase());
    smoothScrollToCatalog();
}

async function applyScannedCodeToForm(code) {
    const nameField = document.getElementById("name");
    const descriptionField = document.getElementById("description");
    const priceField = document.getElementById("price");
    const quantityField = document.getElementById("quantity");

    setScannerStatus(`Looking up ${code} on the web...`);

    try {
        const response = await fetch(`/products/lookup-upc/${encodeURIComponent(code)}`);
        const result = await response.json();

        if (result && result.found) {
            if (nameField) nameField.value = result.name || code;
            if (descriptionField) descriptionField.value = result.description || "";
            if (priceField) priceField.value = result.price || "";
            if (quantityField) quantityField.value = result.quantity || "";
            setScannerStatus(`Found: ${result.name}`);
        } else {
            if (nameField) nameField.value = code;
            setScannerStatus(`No web match for ${code} - name field filled with the code`);
        }
    } catch (e) {
        console.error("UPC lookup failed", e);
        if (nameField) nameField.value = code;
        setScannerStatus("Lookup failed - name field filled with the code");
    }

    if (nameField) {
        nameField.focus();
    }
}

function setScannerStatus(text) {
    const statusEl = document.getElementById("scanned-code-value");
    if (statusEl) {
        statusEl.textContent = text;
    }
}

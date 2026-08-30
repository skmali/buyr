let activeScannerTab = "barcode";
let html5QrScanner = null;
let isQuaggaRunning = false;

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

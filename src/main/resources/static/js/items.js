const ITEMS_API_BASE = '/api/items';
const MAX_IMAGES = 6;
const CATEGORY_OPTIONS = [
    'ELECTRONICS',
    'FURNITURE',
    'CLOTHING',
    'BOOKS',
    'ART',
    'VEHICLES',
    'REAL_ESTATE',
    'JEWELRY',
    'COLLECTIBLES',
    'SPORTS',
    'HOME_AND_GARDEN',
    'TOYS',
    'OTHER',
];

const state = {
    items: [],
    editingItem: null,
    baseImageIndex: 0,
};

document.addEventListener('DOMContentLoaded', () => {
    const page = document.querySelector('[data-page="items"]');
    if (!page) return;

    if (!ensureLoggedIn()) return;

    populateCategories();
    bindFormEvents();
    bindItemsActions();
    resetForm();
    loadItems();
});

function ensureLoggedIn() {
    if (!getCurrentUser()) {
        window.location.href = 'login.html';
        return false;
    }
    return true;
}

function populateCategories() {
    const select = document.getElementById('category');
    if (!select) return;

    CATEGORY_OPTIONS.forEach((value) => {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = formatCategory(value);
        select.appendChild(option);
    });
}

function bindFormEvents() {
    const form = document.getElementById('itemForm');
    const addImageBtn = document.getElementById('addImageBtn');
    const cancelEditBtn = document.getElementById('cancelEditBtn');
    const refreshBtn = document.getElementById('refreshBtn');

    form?.addEventListener('submit', handleFormSubmit);
    addImageBtn?.addEventListener('click', () => addImageRow());
    cancelEditBtn?.addEventListener('click', resetForm);
    refreshBtn?.addEventListener('click', loadItems);
}

function bindItemsActions() {
    const grid = document.getElementById('itemsGrid');
    if (!grid) return;

    grid.addEventListener('click', (event) => {
        const button = event.target.closest('[data-action]');
        if (!button) return;

        const card = button.closest('.item-card');
        if (!card) return;

        const itemId = Number(card.dataset.itemId);
        if (!itemId) return;

        const action = button.dataset.action;
        if (action === 'edit-item') {
            const item = state.items.find((entry) => Number(entry.id) === itemId);
            if (item) {
                enterEditMode(item);
            }
            return;
        }

        if (action === 'swap-image') {
            const first = card.querySelector('[data-role="swap-first"]');
            const second = card.querySelector('[data-role="swap-second"]');
            const firstIndex = parseInt(first?.value, 10);
            const secondIndex = parseInt(second?.value, 10);
            if (Number.isNaN(firstIndex) || Number.isNaN(secondIndex)) {
                showAlert('Enter valid image indexes to swap.', 'error');
                return;
            }
            swapImages(itemId, firstIndex, secondIndex);
            return;
        }

        if (action === 'replace-image') {
            const imageIdInput = card.querySelector('[data-role="image-id"]');
            const imageUrlInput = card.querySelector('[data-role="image-url"]');
            const imageId = Number(imageIdInput?.value);
            const imageUrl = imageUrlInput?.value.trim();

            if (!imageId || !imageUrl) {
                showAlert('Provide both an image ID and replacement URL.', 'error');
                return;
            }
            replaceImage(itemId, imageId, imageUrl);
            return;
        }

        if (action === 'remove-image') {
            const imageIdInput = card.querySelector('[data-role="image-id"]');
            const imageId = Number(imageIdInput?.value);
            if (!imageId) {
                showAlert('Provide an image ID to remove.', 'error');
                return;
            }
            removeImage(itemId, imageId);
        }
    });
}

function resetForm() {
    state.editingItem = null;
    state.baseImageIndex = 0;

    const form = document.getElementById('itemForm');
    form?.reset();

    setFieldValue('title', '');
    setFieldValue('description', '');
    setFieldValue('category', '');
    setFieldValue('startingPrice', '');
    setFieldValue('startDate', '');
    setFieldValue('endDate', '');

    renderExistingImages([]);
    setImageRows(1);
    setFormMode(false);
}

function enterEditMode(item) {
    state.editingItem = item;
    state.baseImageIndex = Array.isArray(item.imagesUrlList) ? item.imagesUrlList.length : 0;

    setFieldValue('title', item.title || '');
    setFieldValue('description', item.description || '');
    setFieldValue('category', item.category || '');
    setFieldValue('startingPrice', item.startingPrice ?? '');
    setFieldValue('startDate', formatDateTimeInput(item.startDate));
    setFieldValue('endDate', formatDateTimeInput(item.endDate));

    renderExistingImages(item.imagesUrlList || []);
    setImageRows(1);
    setFormMode(true, item.id);
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function setFormMode(isEdit, itemId) {
    const formTitle = document.getElementById('formTitle');
    const formHint = document.getElementById('formHint');
    const editNote = document.getElementById('editNote');
    const submitText = document.getElementById('itemSubmitText');
    const cancelBtn = document.getElementById('cancelEditBtn');

    const category = document.getElementById('category');
    const startingPrice = document.getElementById('startingPrice');
    const startDate = document.getElementById('startDate');
    const endDate = document.getElementById('endDate');

    if (isEdit) {
        formTitle.textContent = `Editing item #${itemId}`;
        formHint.textContent = 'Update the title, description, and add new images.';
        editNote.classList.remove('hidden');
        submitText.textContent = 'Save Changes';
        cancelBtn.classList.remove('hidden');
        category.disabled = true;
        startingPrice.disabled = true;
        startDate.disabled = true;
        endDate.disabled = true;
    } else {
        formTitle.textContent = 'Create a listing';
        formHint.textContent = 'Fill in the details for your auction item.';
        editNote.classList.add('hidden');
        submitText.textContent = 'Create Item';
        cancelBtn.classList.add('hidden');
        category.disabled = false;
        startingPrice.disabled = false;
        startDate.disabled = false;
        endDate.disabled = false;
    }
}

function setFieldValue(id, value) {
    const field = document.getElementById(id);
    if (field) {
        field.value = value;
    }
}

function setImageRows(count) {
    const list = document.getElementById('imagesList');
    if (!list) return;

    list.innerHTML = '';
    for (let i = 0; i < count; i += 1) {
        addImageRow();
    }
    updateAddImageState();
}

function addImageRow() {
    const list = document.getElementById('imagesList');
    if (!list) return;

    const currentRows = list.querySelectorAll('.image-row').length;
    if (state.baseImageIndex + currentRows >= MAX_IMAGES) {
        showAlert('You can only add up to 6 images total.', 'error');
        return;
    }

    const index = state.baseImageIndex + currentRows;
    const row = document.createElement('div');
    row.className = 'image-row';
    row.dataset.index = String(index);

    const indexBadge = document.createElement('div');
    indexBadge.className = 'image-row-index';
    indexBadge.textContent = `#${index}`;

    const input = document.createElement('input');
    input.type = 'url';
    input.placeholder = 'https://image-url.com/item.jpg';
    input.autocomplete = 'off';

    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn-outline btn-sm';
    removeBtn.innerHTML = '<i class="fas fa-minus"></i> Remove';
    removeBtn.addEventListener('click', () => {
        const rows = list.querySelectorAll('.image-row');
        if (!state.editingItem && rows.length <= 1) {
            input.value = '';
            return;
        }
        row.remove();
        reindexImageRows();
        updateAddImageState();
    });

    row.append(indexBadge, input, removeBtn);
    list.appendChild(row);
    updateAddImageState();
}

function reindexImageRows() {
    const list = document.getElementById('imagesList');
    if (!list) return;

    const rows = list.querySelectorAll('.image-row');
    rows.forEach((row, idx) => {
        const newIndex = state.baseImageIndex + idx;
        row.dataset.index = String(newIndex);
        const badge = row.querySelector('.image-row-index');
        if (badge) {
            badge.textContent = `#${newIndex}`;
        }
    });
}

function updateAddImageState() {
    const addBtn = document.getElementById('addImageBtn');
    const list = document.getElementById('imagesList');
    if (!addBtn || !list) return;

    const currentRows = list.querySelectorAll('.image-row').length;
    addBtn.disabled = state.baseImageIndex + currentRows >= MAX_IMAGES;
}

async function handleFormSubmit(event) {
    event.preventDefault();

    const title = document.getElementById('title')?.value.trim();
    const description = document.getElementById('description')?.value.trim();
    const category = document.getElementById('category')?.value;
    const startingPriceValue = document.getElementById('startingPrice')?.value;
    const startDateValue = document.getElementById('startDate')?.value;
    const endDateValue = document.getElementById('endDate')?.value;

    if (!title || !description) {
        showAlert('Title and description are required.', 'error');
        return;
    }

    const { images, error } = collectImages();
    if (error) {
        showAlert(error, 'error');
        return;
    }

    const isEdit = Boolean(state.editingItem);
    if (!isEdit) {
        if (!category) {
            showAlert('Select a category.', 'error');
            return;
        }

        const startingPrice = Number(startingPriceValue);
        if (Number.isNaN(startingPrice)) {
            showAlert('Enter a valid starting price.', 'error');
            return;
        }

        const startDate = normalizeLocalDateTime(startDateValue);
        const endDate = normalizeLocalDateTime(endDateValue);
        if (!startDate || !endDate) {
            showAlert('Start and end dates are required.', 'error');
            return;
        }

        if (new Date(startDate) >= new Date(endDate)) {
            showAlert('End date must be after the start date.', 'error');
            return;
        }

        if (images.length === 0) {
            showAlert('Add at least one image URL.', 'error');
            return;
        }

        const payload = {
            title,
            description,
            category,
            startingPrice,
            startDate,
            endDate,
            imagesUrlList: images,
        };

        await submitItem(payload, 'POST', ITEMS_API_BASE, 'Item created successfully.');
        return;
    }

    const payload = {
        title,
        description,
    };
    if (images.length > 0) {
        payload.imagesUrlList = images;
    }

    await submitItem(payload, 'PUT', `${ITEMS_API_BASE}/${state.editingItem.id}`, 'Item updated successfully.');
}

async function submitItem(payload, method, url, successMessage) {
    setFormLoading(true);
    try {
        const response = await fetchWithAuth(url, {
            method,
            body: JSON.stringify(payload),
        });
        const data = await safeJson(response);
        if (!response.ok) {
            showAlert(getErrorMessage(data, 'Request failed.'), 'error');
            return;
        }

        showAlert(successMessage, 'success');
        resetForm();
        await loadItems();
    } catch (error) {
        console.error('Item request error:', error);
        showAlert('Something went wrong. Please try again.', 'error');
    } finally {
        setFormLoading(false);
    }
}

async function loadItems() {
    const grid = document.getElementById('itemsGrid');
    if (!grid) return;

    grid.innerHTML = '<div class="items-loading">Loading listings...</div>';

    try {
        const response = await fetchWithAuth(`${ITEMS_API_BASE}/user`);
        const data = await safeJson(response);

        if (!response.ok) {
            showAlert(getErrorMessage(data, 'Unable to load items.'), 'error');
            grid.innerHTML = '<div class="items-empty">Unable to load items right now.</div>';
            return;
        }

        state.items = Array.isArray(data) ? data : [];
        renderItems();
    } catch (error) {
        console.error('Items load error:', error);
        showAlert('Unable to load items right now.', 'error');
        grid.innerHTML = '<div class="items-empty">Unable to load items right now.</div>';
    }
}

function renderItems() {
    const grid = document.getElementById('itemsGrid');
    if (!grid) return;

    updateSummary();

    if (state.items.length === 0) {
        grid.innerHTML = '<div class="items-empty">No listings yet. Create your first item to get started.</div>';
        return;
    }

    grid.innerHTML = '';
    state.items.forEach((item) => {
        grid.appendChild(buildItemCard(item));
    });
}

function buildItemCard(item) {
    const card = document.createElement('article');
    card.className = 'item-card';
    card.dataset.itemId = item.id;

    const images = Array.isArray(item.imagesUrlList) ? item.imagesUrlList : [];
    const status = getStatus(item);

    const imagesMarkup = images.length
        ? images
            .map((url, index) => {
                const safeUrl = escapeHtml(url);
                return `
                    <div class="item-image">
                        <img src="${safeUrl}" alt="Item image ${index + 1}" loading="lazy">
                        <span>#${index}</span>
                    </div>
                `;
            })
            .join('')
        : `
            <div class="item-image">
                <div class="item-image-placeholder">No images yet</div>
            </div>
        `;

    card.innerHTML = `
        <div class="item-card-header">
            <div class="item-card-title">
                <h3>${escapeHtml(item.title || 'Untitled item')}</h3>
                <span class="item-status ${status.className}">${status.label}</span>
            </div>
            <button type="button" class="btn btn-outline btn-sm" data-action="edit-item">
                <i class="fas fa-pen"></i>
                Edit
            </button>
        </div>
        <p class="item-description">${escapeHtml(item.description || 'No description provided.')}</p>
        <div class="item-meta">
            <span><i class="fas fa-hashtag"></i>ID ${escapeHtml(item.id)}</span>
            <span><i class="fas fa-tag"></i>${escapeHtml(formatCategory(item.category || 'OTHER'))}</span>
            <span><i class="fas fa-coins"></i>Start ${formatCurrency(item.startingPrice)}</span>
            <span><i class="fas fa-chart-line"></i>Current ${formatCurrency(item.currentPrice)}</span>
            <span><i class="fas fa-clock"></i>Start ${escapeHtml(formatDateTime(item.startDate))}</span>
            <span><i class="fas fa-flag-checkered"></i>End ${escapeHtml(formatDateTime(item.endDate))}</span>
        </div>
        <div class="item-images">
            ${imagesMarkup}
        </div>
        <div class="item-tools">
            <div>
                <strong>Swap image order</strong>
                <div class="swap-controls">
                    <input type="number" min="0" max="5" value="0" data-role="swap-first">
                    <input type="number" min="0" max="5" value="${images.length > 1 ? 1 : 0}" data-role="swap-second">
                    <button type="button" class="btn btn-outline btn-sm" data-action="swap-image">
                        <i class="fas fa-arrows-rotate"></i>
                        Swap
                    </button>
                </div>
            </div>
            <details class="item-advanced">
                <summary>Replace or remove by image ID</summary>
                <div class="advanced-grid">
                    <div class="form-group">
                        <label>Image ID</label>
                        <input type="number" min="1" placeholder="Image ID" data-role="image-id">
                    </div>
                    <div class="form-group">
                        <label>New URL (for replace)</label>
                        <input type="url" placeholder="https://..." data-role="image-url">
                    </div>
                    <button type="button" class="btn btn-outline btn-sm" data-action="replace-image">
                        <i class="fas fa-pen"></i>
                        Replace
                    </button>
                    <button type="button" class="btn btn-danger btn-sm" data-action="remove-image">
                        <i class="fas fa-trash"></i>
                        Remove
                    </button>
                </div>
                <p class="helper-text">Image IDs are not returned by the list endpoint. Paste one from an admin view or database.</p>
            </details>
        </div>
    `;

    return card;
}

function updateSummary() {
    const total = state.items.length;
    const active = state.items.filter((item) => item.isActive === true).length;
    const ended = state.items.filter((item) => item.isActive === false).length;

    setTextContent('totalCount', total);
    setTextContent('activeCount', active);
    setTextContent('endedCount', ended);
}

function setTextContent(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.textContent = value;
    }
}

function collectImages() {
    const list = document.getElementById('imagesList');
    const rows = list ? Array.from(list.querySelectorAll('.image-row')) : [];
    const images = [];
    let emptyRows = 0;

    rows.forEach((row) => {
        const input = row.querySelector('input');
        const url = input?.value.trim();
        if (!url) {
            emptyRows += 1;
            return;
        }
        images.push({
            url,
            index: Number(row.dataset.index),
        });
    });

    if (images.length > 0 && emptyRows > 0) {
        return { error: 'Fill in all image URLs or remove empty rows.' };
    }

    return { images };
}

function renderExistingImages(images) {
    const panel = document.getElementById('existingImagesPanel');
    const container = document.getElementById('existingImages');
    if (!panel || !container) return;

    container.innerHTML = '';

    if (!Array.isArray(images) || images.length === 0) {
        panel.classList.add('hidden');
        return;
    }

    panel.classList.remove('hidden');

    images.forEach((url, index) => {
        const wrapper = document.createElement('div');
        wrapper.className = 'existing-image';

        const img = document.createElement('img');
        img.src = url;
        img.alt = `Existing image ${index + 1}`;

        const badge = document.createElement('span');
        badge.textContent = `#${index}`;

        wrapper.append(img, badge);
        container.appendChild(wrapper);
    });
}

function getStatus(item) {
    if (item.isActive === true) {
        return { label: 'Active', className: 'status-active' };
    }
    if (item.isActive === false) {
        return { label: 'Inactive', className: 'status-inactive' };
    }
    return { label: 'Unknown', className: 'status-unknown' };
}

async function swapImages(itemId, firstIndex, secondIndex) {
    try {
        const response = await fetchWithAuth(
            `${ITEMS_API_BASE}/${itemId}/images/swap?fIndex=${firstIndex}&sIndex=${secondIndex}`,
            { method: 'POST' }
        );

        if (!response.ok) {
            const data = await safeJson(response);
            showAlert(getErrorMessage(data, 'Unable to swap images.'), 'error');
            return;
        }

        showAlert('Image order updated.', 'success');
        await loadItems();
    } catch (error) {
        console.error('Swap image error:', error);
        showAlert('Unable to swap images.', 'error');
    }
}

async function replaceImage(itemId, imageId, imageUrl) {
    try {
        const payload = {
            imagesUrlList: [
                { url: imageUrl, index: 0 },
            ],
        };

        const response = await fetchWithAuth(`${ITEMS_API_BASE}/${itemId}/images/${imageId}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        });

        const data = await safeJson(response);
        if (!response.ok) {
            showAlert(getErrorMessage(data, 'Unable to replace the image.'), 'error');
            return;
        }

        showAlert('Image replaced successfully.', 'success');
        await loadItems();
    } catch (error) {
        console.error('Replace image error:', error);
        showAlert('Unable to replace the image.', 'error');
    }
}

async function removeImage(itemId, imageId) {
    try {
        const response = await fetchWithAuth(`${ITEMS_API_BASE}/${itemId}/images/${imageId}`, {
            method: 'DELETE',
        });

        const data = await safeJson(response);
        if (!response.ok) {
            showAlert(getErrorMessage(data, 'Unable to remove the image.'), 'error');
            return;
        }

        showAlert('Image removed successfully.', 'success');
        await loadItems();
    } catch (error) {
        console.error('Remove image error:', error);
        showAlert('Unable to remove the image.', 'error');
    }
}

function setFormLoading(loading) {
    const submitBtn = document.getElementById('itemSubmitBtn');
    const submitText = document.getElementById('itemSubmitText');
    const spinner = document.getElementById('itemSubmitSpinner');

    if (submitBtn) submitBtn.disabled = loading;
    if (submitText) submitText.style.display = loading ? 'none' : 'inline';
    if (spinner) spinner.style.display = loading ? 'block' : 'none';
}

function normalizeLocalDateTime(value) {
    if (!value) return null;
    return value.length === 16 ? `${value}:00` : value;
}

function formatDateTime(value) {
    if (!value) return '-';
    if (typeof value === 'string') {
        return value.replace('T', ' ').split('.')[0];
    }
    try {
        return new Date(value).toLocaleString();
    } catch (error) {
        return String(value);
    }
}

function formatDateTimeInput(value) {
    if (!value || typeof value !== 'string') return '';
    return value.replace('T', ' ').split('.')[0].replace(' ', 'T').slice(0, 16);
}

function formatCurrency(value) {
    if (value === null || value === undefined || value === '') return '-';
    const number = Number(value);
    if (Number.isNaN(number)) return String(value);
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD',
        maximumFractionDigits: 2,
    }).format(number);
}

function formatCategory(value) {
    if (!value) return 'Other';
    return String(value)
        .split('_')
        .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
        .join(' ');
}

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function getErrorMessage(data, fallback) {
    if (!data) return fallback;
    if (Array.isArray(data.errors)) return data.errors.join(', ');
    return data.message || data.error || fallback;
}

async function safeJson(response) {
    try {
        return await response.json();
    } catch (error) {
        return null;
    }
}

// Same-origin paths: nginx in this container reverse-proxies them to the
// services, so no environment-specific URLs are baked into the build.
const API_ENDPOINTS = {
    orders: '/api/orders',
    inventory: '/api/inventory',
    payments: '/api/payments'
};

// Values coming back from the API are rendered as HTML, so they must be escaped.
function escapeHtml(value) {
    if (value === null || value === undefined) {
        return '';
    }
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function formatMoney(value) {
    const amount = Number(value);
    return Number.isFinite(amount) ? amount.toFixed(2) : '0.00';
}

function shortId(value) {
    if (!value) {
        return 'N/A';
    }
    return escapeHtml(String(value).substring(0, 8)) + '...';
}

function renderMessage(tbody, columns, message, isError) {
    const style = isError ? ' style="color: #e74c3c;"' : '';
    tbody.innerHTML = `<tr><td colspan="${columns}" class="loading"${style}>${escapeHtml(message)}</td></tr>`;
}

// Surfaces the API's error message instead of assuming the body is JSON.
async function errorMessageFrom(response, fallback) {
    try {
        const body = await response.json();
        if (body && body.message) {
            return body.message;
        }
    } catch (ignored) {
        // Non-JSON error body (e.g. a proxy error page).
    }
    return `${fallback} (HTTP ${response.status})`;
}

// Tab Management
function showTab(tabName, evt) {
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });

    document.getElementById(`${tabName}-tab`).classList.add('active');
    if (evt && evt.currentTarget) {
        evt.currentTarget.classList.add('active');
    }

    if (tabName === 'orders') {
        loadOrders();
    } else if (tabName === 'inventory') {
        loadInventory();
    } else if (tabName === 'payments') {
        loadPayments();
    }
}

// Load Orders
async function loadOrders() {
    const tbody = document.getElementById('orders-tbody');
    renderMessage(tbody, 5, 'Loading orders...', false);

    try {
        const response = await fetch(API_ENDPOINTS.orders);
        if (!response.ok) {
            throw new Error(await errorMessageFrom(response, 'Failed to fetch orders'));
        }

        const orders = await response.json();

        if (orders.length === 0) {
            renderMessage(tbody, 5, 'No orders found', false);
            return;
        }

        tbody.innerHTML = orders.map(order => `
            <tr>
                <td>${shortId(order.id)}</td>
                <td>${escapeHtml(order.productId || 'N/A')}</td>
                <td>${escapeHtml(order.quantity)}</td>
                <td>$${formatMoney(order.price)}</td>
                <td>$${formatMoney(Number(order.quantity) * Number(order.price))}</td>
            </tr>
        `).join('');
    } catch (error) {
        renderMessage(tbody, 5, `Error: ${error.message}`, true);
        console.error('Error loading orders:', error);
    }
}

// Load Inventory
async function loadInventory() {
    const tbody = document.getElementById('inventory-tbody');
    renderMessage(tbody, 2, 'Loading inventory...', false);

    try {
        const response = await fetch(API_ENDPOINTS.inventory);
        if (!response.ok) {
            throw new Error(await errorMessageFrom(response, 'Failed to fetch inventory'));
        }

        const inventory = await response.json();

        if (inventory.length === 0) {
            renderMessage(tbody, 2, 'No inventory items found', false);
            return;
        }

        tbody.innerHTML = inventory.map(item => `
            <tr>
                <td>${escapeHtml(item.productId || 'N/A')}</td>
                <td>${escapeHtml(item.availableQuantity || 0)}</td>
            </tr>
        `).join('');
    } catch (error) {
        renderMessage(tbody, 2, `Error: ${error.message}`, true);
        console.error('Error loading inventory:', error);
    }
}

// Load Payments
async function loadPayments() {
    const tbody = document.getElementById('payments-tbody');
    renderMessage(tbody, 4, 'Loading payments...', false);

    try {
        const response = await fetch(API_ENDPOINTS.payments);
        if (!response.ok) {
            throw new Error(await errorMessageFrom(response, 'Failed to fetch payments'));
        }

        const payments = await response.json();

        if (payments.length === 0) {
            renderMessage(tbody, 4, 'No payments found', false);
            return;
        }

        tbody.innerHTML = payments.map(payment => {
            const status = payment.status || 'PENDING';
            return `
            <tr>
                <td>${shortId(payment.id)}</td>
                <td>${shortId(payment.orderId)}</td>
                <td>$${formatMoney(payment.amount)}</td>
                <td><span class="status-badge ${escapeHtml(status.toLowerCase())}">${escapeHtml(status)}</span></td>
            </tr>
            `;
        }).join('');
    } catch (error) {
        renderMessage(tbody, 4, `Error: ${error.message}`, true);
        console.error('Error loading payments:', error);
    }
}

// Create Order Modal
function showCreateOrderModal() {
    document.getElementById('create-order-modal').style.display = 'block';
}

function closeCreateOrderModal() {
    document.getElementById('create-order-modal').style.display = 'none';
    document.getElementById('create-order-form').reset();
}

// Create Order
async function createOrder(event) {
    event.preventDefault();

    const formData = {
        productId: document.getElementById('productId').value,
        quantity: parseInt(document.getElementById('quantity').value, 10),
        price: parseFloat(document.getElementById('price').value)
    };

    try {
        const response = await fetch(API_ENDPOINTS.orders, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });

        if (!response.ok) {
            throw new Error(await errorMessageFrom(response, 'Failed to create order'));
        }

        const order = await response.json();
        alert(`Order created successfully! ID: ${String(order.id).substring(0, 8)}...`);

        closeCreateOrderModal();
        loadOrders();

        // Inventory and payments are updated asynchronously by the consumers.
        setTimeout(() => {
            loadInventory();
            loadPayments();
        }, 2000);
    } catch (error) {
        alert(`Error creating order: ${error.message}`);
        console.error('Error creating order:', error);
    }
}

// Create Inventory Modal
function showCreateInventoryModal() {
    document.getElementById('create-inventory-modal').style.display = 'block';
}

function closeCreateInventoryModal() {
    document.getElementById('create-inventory-modal').style.display = 'none';
    document.getElementById('create-inventory-form').reset();
}

// Create Inventory Item
async function createInventoryItem(event) {
    event.preventDefault();

    const formData = {
        productId: document.getElementById('inv-productId').value,
        availableQuantity: parseInt(document.getElementById('availableQuantity').value, 10)
    };

    try {
        const response = await fetch(API_ENDPOINTS.inventory, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });

        if (!response.ok) {
            throw new Error(await errorMessageFrom(response, 'Failed to save inventory item'));
        }

        const item = await response.json();
        alert(`Inventory saved! Product: ${item.productId}`);

        closeCreateInventoryModal();
        loadInventory();
    } catch (error) {
        alert(`Error saving inventory item: ${error.message}`);
        console.error('Error saving inventory item:', error);
    }
}

// Create Payment Modal
function showCreatePaymentModal() {
    document.getElementById('create-payment-modal').style.display = 'block';
}

function closeCreatePaymentModal() {
    document.getElementById('create-payment-modal').style.display = 'none';
    document.getElementById('create-payment-form').reset();
}

// Create Payment
async function createPayment(event) {
    event.preventDefault();

    const orderId = document.getElementById('payment-orderId').value;
    const amount = document.getElementById('payment-amount').value;
    const query = new URLSearchParams({ orderId, amount }).toString();

    try {
        const response = await fetch(`${API_ENDPOINTS.payments}?${query}`, {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error(await errorMessageFrom(response, 'Failed to create payment'));
        }

        const payment = await response.json();
        alert(`Payment created successfully! ID: ${String(payment.id).substring(0, 8)}...`);

        closeCreatePaymentModal();
        loadPayments();
    } catch (error) {
        alert(`Error creating payment: ${error.message}`);
        console.error('Error creating payment:', error);
    }
}

// Close modal when clicking outside
window.onclick = function (event) {
    const orderModal = document.getElementById('create-order-modal');
    const inventoryModal = document.getElementById('create-inventory-modal');
    const paymentModal = document.getElementById('create-payment-modal');

    if (event.target === orderModal) {
        closeCreateOrderModal();
    }
    if (event.target === inventoryModal) {
        closeCreateInventoryModal();
    }
    if (event.target === paymentModal) {
        closeCreatePaymentModal();
    }
};

// Load orders on page load
document.addEventListener('DOMContentLoaded', () => {
    loadOrders();
});

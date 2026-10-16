(() => {
  'use strict';

  const API_BASE = document.querySelector('meta[name="api-base"]')?.content || '/api';
  const CART_STORAGE_KEY = 'shopkart.cart.v1';
  const CUSTOMER_STORAGE_KEY = 'shopkart.customer.v1';
  const CHECKOUT_KEY = 'shopkart.checkout-key.v1';
  const MAX_QUANTITY = 10;

  const imageBySku = {
    'TSHIRT-001': '/assets/products/tshirt.jpg',
    'SHOE-001': '/assets/products/sneakers.jpg',
    'AUDIO-001': '/assets/products/headphones.jpg',
    'WATCH-001': '/assets/products/watch.jpg',
    'BAG-001': '/assets/products/backpack.jpg',
    'SUN-001': '/assets/products/sunglasses.jpg'
  };

  const fallbackImage = '/assets/products/backpack.jpg';
  const money = new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0
  });

  const state = {
    products: [],
    cart: readCart(),
    category: 'all',
    query: '',
    loading: true,
    drawerOpen: false
  };

  const elements = {
    search: document.querySelector('#product-search'),
    productGrid: document.querySelector('#product-grid'),
    productCount: document.querySelector('#product-count'),
    categoryBar: document.querySelector('#category-bar'),
    status: document.querySelector('#catalog-status'),
    statusTitle: document.querySelector('#status-title'),
    statusMessage: document.querySelector('#status-message'),
    retry: document.querySelector('#retry-catalog'),
    emptyResults: document.querySelector('#empty-results'),
    clearFilters: document.querySelector('#clear-filters'),
    cartTrigger: document.querySelector('#cart-trigger'),
    cartCount: document.querySelector('#cart-count'),
    cartDrawer: document.querySelector('#cart-drawer'),
    drawerBackdrop: document.querySelector('#drawer-backdrop'),
    closeCart: document.querySelector('#close-cart'),
    continueShopping: document.querySelector('#continue-shopping'),
    cartBody: document.querySelector('#cart-body'),
    emptyCart: document.querySelector('#empty-cart'),
    cartItems: document.querySelector('#cart-items'),
    cartSummary: document.querySelector('#cart-summary'),
    cartSubtotal: document.querySelector('#cart-subtotal'),
    checkoutButton: document.querySelector('#checkout-button'),
    checkoutWrap: document.querySelector('#checkout-form-wrap'),
    checkoutForm: document.querySelector('#checkout-form'),
    checkoutEmail: document.querySelector('#checkout-email'),
    checkoutPhone: document.querySelector('#checkout-phone'),
    checkoutError: document.querySelector('#checkout-error'),
    placeOrder: document.querySelector('#place-order'),
    backToCart: document.querySelector('#back-to-cart'),
    orderSuccess: document.querySelector('#order-success'),
    successMessage: document.querySelector('#success-message'),
    finishOrder: document.querySelector('#finish-order'),
    toast: document.querySelector('#toast'),
    toastMessage: document.querySelector('#toast-message')
  };

  let toastTimer;
  let searchTimer;
  let lastFocusedElement;

  function readCart() {
    try {
      const saved = JSON.parse(localStorage.getItem(CART_STORAGE_KEY) || '[]');
      return Array.isArray(saved) ? saved.filter(isStoredCartItem) : [];
    } catch {
      return [];
    }
  }

  function isStoredCartItem(item) {
    return item
      && typeof item.sku === 'string'
      && typeof item.name === 'string'
      && Number.isFinite(Number(item.price))
      && Number.isInteger(item.quantity)
      && item.quantity > 0;
  }

  function persistCart() {
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(state.cart));
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function imageFor(product) {
    return imageBySku[product.sku] || fallbackImage;
  }

  function displayCategory(category) {
    return String(category || 'Other')
      .replaceAll('-', ' ')
      .replace(/\b\w/g, character => character.toUpperCase());
  }

  function refreshIcons() {
    if (window.lucide) {
      window.lucide.createIcons({attrs: {'stroke-width': 1.8}});
    }
  }

  function showSkeletons() {
    elements.productGrid.innerHTML = Array.from({length: 6}, () => `
      <article class="skeleton-card" aria-hidden="true">
        <div class="skeleton-block skeleton-media"></div>
        <div class="skeleton-block skeleton-line short"></div>
        <div class="skeleton-block skeleton-line"></div>
      </article>
    `).join('');
  }

  async function loadProducts() {
    state.loading = true;
    elements.status.hidden = true;
    elements.emptyResults.hidden = true;
    showSkeletons();

    try {
      const response = await fetch(`${API_BASE}/products`, {
        headers: {'Accept': 'application/json'}
      });
      if (!response.ok) {
        throw new Error(`Catalog returned ${response.status}`);
      }
      const products = await response.json();
      state.products = Array.isArray(products) ? products.filter(product => product.active !== false) : [];
      reconcileCart();
      renderCategories();
      renderProducts();
    } catch (error) {
      state.products = [];
      elements.productGrid.innerHTML = '';
      elements.status.hidden = false;
      elements.statusTitle.textContent = 'Catalog temporarily unavailable';
      elements.statusMessage.textContent = 'The shop could not load its products. Please try again.';
      console.error(error);
    } finally {
      state.loading = false;
      window.__SHOPKART_READY__ = true;
      refreshIcons();
    }
  }

  function reconcileCart() {
    const productBySku = new Map(state.products.map(product => [product.sku, product]));
    state.cart = state.cart
      .filter(item => productBySku.has(item.sku))
      .map(item => {
        const current = productBySku.get(item.sku);
        return {
          sku: current.sku,
          name: current.name,
          brand: current.brand,
          price: Number(current.price),
          image: imageFor(current),
          quantity: Math.min(item.quantity, MAX_QUANTITY)
        };
      });
    persistCart();
    renderCart();
  }

  function renderCategories() {
    const categories = [...new Set(state.products.map(product => displayCategory(product.category)))].sort();
    elements.categoryBar.innerHTML = ['All', ...categories].map(category => {
      const value = category === 'All' ? 'all' : category.toLowerCase();
      const active = state.category === value;
      return `<button class="category-option${active ? ' is-active' : ''}" type="button" data-category="${escapeHtml(value)}" aria-pressed="${active}">${escapeHtml(category)}</button>`;
    }).join('');
  }

  function visibleProducts() {
    const query = state.query.trim().toLowerCase();
    return state.products.filter(product => {
      const categoryMatches = state.category === 'all'
        || displayCategory(product.category).toLowerCase() === state.category;
      const queryMatches = !query
        || [product.name, product.brand, product.category, product.description, product.sku]
          .some(value => String(value || '').toLowerCase().includes(query));
      return categoryMatches && queryMatches;
    });
  }

  function renderProducts() {
    const products = visibleProducts();
    elements.productCount.textContent = String(products.length);
    elements.emptyResults.hidden = products.length > 0 || state.loading;

    elements.productGrid.innerHTML = products.map((product, index) => `
      <article class="product-card" style="animation-delay:${Math.min(index * 55, 275)}ms" data-sku="${escapeHtml(product.sku)}">
        <figure class="product-media">
          <img src="${imageFor(product)}" alt="${escapeHtml(product.name)}" loading="${index < 3 ? 'eager' : 'lazy'}" width="720" height="900">
          <span class="product-category">${escapeHtml(displayCategory(product.category))}</span>
        </figure>
        <div class="product-info">
          <p class="product-brand">${escapeHtml(product.brand)}</p>
          <h2 class="product-name">${escapeHtml(product.name)}</h2>
          <p class="product-description">${escapeHtml(product.description)}</p>
          <div class="product-footer">
            <span class="product-price">${money.format(Number(product.price))}</span>
            <button class="add-button" type="button" data-action="add" data-sku="${escapeHtml(product.sku)}">
              <i data-lucide="plus" aria-hidden="true"></i>
              Add to cart
            </button>
          </div>
        </div>
      </article>
    `).join('');

    refreshIcons();
  }

  function addToCart(sku) {
    const product = state.products.find(item => item.sku === sku);
    if (!product) return;

    const existing = state.cart.find(item => item.sku === sku);
    if (existing) {
      existing.quantity = Math.min(existing.quantity + 1, MAX_QUANTITY);
    } else {
      state.cart.push({
        sku: product.sku,
        name: product.name,
        brand: product.brand,
        price: Number(product.price),
        image: imageFor(product),
        quantity: 1
      });
    }
    persistCart();
    renderCart();
    showToast(`${product.name} added`);
  }

  function updateQuantity(sku, change) {
    const item = state.cart.find(entry => entry.sku === sku);
    if (!item) return;
    item.quantity = Math.max(0, Math.min(item.quantity + change, MAX_QUANTITY));
    if (item.quantity === 0) {
      state.cart = state.cart.filter(entry => entry.sku !== sku);
    }
    persistCart();
    renderCart();
  }

  function removeFromCart(sku) {
    state.cart = state.cart.filter(item => item.sku !== sku);
    persistCart();
    renderCart();
  }

  function renderCart() {
    const itemCount = state.cart.reduce((sum, item) => sum + item.quantity, 0);
    const subtotal = state.cart.reduce((sum, item) => sum + item.price * item.quantity, 0);
    elements.cartCount.textContent = String(itemCount);
    elements.cartCount.setAttribute('aria-label', `${itemCount} ${itemCount === 1 ? 'item' : 'items'}`);
    elements.emptyCart.hidden = state.cart.length > 0;
    elements.cartSummary.hidden = state.cart.length === 0 || !elements.checkoutWrap.hidden || !elements.orderSuccess.hidden;

    elements.cartItems.innerHTML = state.cart.map(item => `
      <article class="cart-item" data-sku="${escapeHtml(item.sku)}">
        <img src="${escapeHtml(item.image)}" alt="" width="156" height="188">
        <div class="cart-item-details">
          <p class="cart-item-brand">${escapeHtml(item.brand)}</p>
          <h3 class="cart-item-name">${escapeHtml(item.name)}</h3>
          <div class="quantity-control" aria-label="Quantity for ${escapeHtml(item.name)}">
            <button type="button" data-action="decrease" data-sku="${escapeHtml(item.sku)}" aria-label="Decrease quantity">
              <i data-lucide="minus" aria-hidden="true"></i>
            </button>
            <span aria-live="polite">${item.quantity}</span>
            <button type="button" data-action="increase" data-sku="${escapeHtml(item.sku)}" aria-label="Increase quantity" ${item.quantity >= MAX_QUANTITY ? 'disabled' : ''}>
              <i data-lucide="plus" aria-hidden="true"></i>
            </button>
          </div>
        </div>
        <div class="cart-item-end">
          <span class="cart-item-price">${money.format(item.price * item.quantity)}</span>
          <button class="remove-item" type="button" data-action="remove" data-sku="${escapeHtml(item.sku)}" aria-label="Remove ${escapeHtml(item.name)}">
            <i data-lucide="trash-2" aria-hidden="true"></i>
          </button>
        </div>
      </article>
    `).join('');

    elements.cartSubtotal.textContent = money.format(subtotal);
    refreshIcons();
  }

  function openCart() {
    lastFocusedElement = document.activeElement;
    state.drawerOpen = true;
    elements.cartDrawer.classList.add('is-open');
    elements.cartDrawer.setAttribute('aria-hidden', 'false');
    elements.cartTrigger.setAttribute('aria-expanded', 'true');
    elements.drawerBackdrop.hidden = false;
    document.body.classList.add('drawer-open');
    elements.closeCart.focus();
  }

  function closeCart() {
    state.drawerOpen = false;
    elements.cartDrawer.classList.remove('is-open');
    elements.cartDrawer.setAttribute('aria-hidden', 'true');
    elements.cartTrigger.setAttribute('aria-expanded', 'false');
    elements.drawerBackdrop.hidden = true;
    document.body.classList.remove('drawer-open');
    if (lastFocusedElement instanceof HTMLElement) lastFocusedElement.focus();
  }

  function showCheckout() {
    if (state.cart.length === 0) return;
    elements.cartBody.hidden = true;
    elements.cartSummary.hidden = true;
    elements.orderSuccess.hidden = true;
    elements.checkoutWrap.hidden = false;
    elements.checkoutError.hidden = true;
    elements.checkoutEmail.focus();
  }

  function showCartContents() {
    elements.checkoutWrap.hidden = true;
    elements.orderSuccess.hidden = true;
    elements.cartBody.hidden = false;
    renderCart();
  }

  function customerId() {
    let value = localStorage.getItem(CUSTOMER_STORAGE_KEY);
    if (!value) {
      value = `web-${crypto.randomUUID().slice(0, 8)}`;
      localStorage.setItem(CUSTOMER_STORAGE_KEY, value);
    }
    return value;
  }

  async function submitOrder(event) {
    event.preventDefault();
    elements.checkoutError.hidden = true;
    elements.placeOrder.disabled = true;
    elements.placeOrder.innerHTML = '<i data-lucide="loader-circle" aria-hidden="true"></i> Placing order';
    elements.placeOrder.querySelector('svg')?.classList.add('spin');
    refreshIcons();

    let idempotencyKey = sessionStorage.getItem(CHECKOUT_KEY);
    if (!idempotencyKey) {
      idempotencyKey = crypto.randomUUID();
      sessionStorage.setItem(CHECKOUT_KEY, idempotencyKey);
    }

    const payload = {
      customer_id: customerId(),
      items: state.cart.map(item => ({sku: item.sku, quantity: item.quantity})),
      card_token: 'tok_ok',
      email: elements.checkoutEmail.value.trim() || null,
      phone: elements.checkoutPhone.value.trim().replaceAll(' ', '') || null
    };

    try {
      const response = await fetch(`${API_BASE}/orders`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          'Idempotency-Key': idempotencyKey
        },
        body: JSON.stringify(payload)
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(result.detail || `Checkout returned ${response.status}`);
      }
      if (result.status !== 'CONFIRMED') {
        throw new Error(result.failure_reason || 'The order could not be confirmed.');
      }

      sessionStorage.removeItem(CHECKOUT_KEY);
      state.cart = [];
      persistCart();
      elements.checkoutWrap.hidden = true;
      elements.cartBody.hidden = true;
      elements.cartSummary.hidden = true;
      elements.orderSuccess.hidden = false;
      elements.successMessage.textContent = `Order ${String(result.order_id).slice(0, 8)} is confirmed.`;
      renderCart();
      refreshIcons();
    } catch (error) {
      elements.checkoutError.textContent = error.message || 'Checkout is unavailable. Please try again.';
      elements.checkoutError.hidden = false;
    } finally {
      elements.placeOrder.disabled = false;
      elements.placeOrder.innerHTML = '<i data-lucide="package-check" aria-hidden="true"></i> Place demo order';
      refreshIcons();
    }
  }

  function finishOrder() {
    showCartContents();
    closeCart();
    showToast('Order confirmed');
  }

  function showToast(message) {
    clearTimeout(toastTimer);
    elements.toastMessage.textContent = message;
    elements.toast.hidden = false;
    refreshIcons();
    toastTimer = setTimeout(() => {
      elements.toast.hidden = true;
    }, 2200);
  }

  function clearFilters() {
    state.category = 'all';
    state.query = '';
    elements.search.value = '';
    renderCategories();
    renderProducts();
    elements.search.focus();
  }

  elements.productGrid.addEventListener('click', event => {
    const button = event.target.closest('[data-action="add"]');
    if (button) addToCart(button.dataset.sku);
  });

  elements.categoryBar.addEventListener('click', event => {
    const button = event.target.closest('[data-category]');
    if (!button) return;
    state.category = button.dataset.category;
    renderCategories();
    renderProducts();
  });

  elements.search.addEventListener('input', event => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.query = event.target.value;
      renderProducts();
    }, 90);
  });

  elements.cartItems.addEventListener('click', event => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const {action, sku} = button.dataset;
    if (action === 'increase') updateQuantity(sku, 1);
    if (action === 'decrease') updateQuantity(sku, -1);
    if (action === 'remove') removeFromCart(sku);
  });

  elements.cartTrigger.addEventListener('click', openCart);
  elements.closeCart.addEventListener('click', closeCart);
  elements.drawerBackdrop.addEventListener('click', closeCart);
  elements.continueShopping.addEventListener('click', closeCart);
  elements.checkoutButton.addEventListener('click', showCheckout);
  elements.backToCart.addEventListener('click', showCartContents);
  elements.checkoutForm.addEventListener('submit', submitOrder);
  elements.finishOrder.addEventListener('click', finishOrder);
  elements.retry.addEventListener('click', loadProducts);
  elements.clearFilters.addEventListener('click', clearFilters);

  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && state.drawerOpen) closeCart();
    if (event.key === '/' && document.activeElement !== elements.search && !state.drawerOpen) {
      event.preventDefault();
      elements.search.focus();
    }
  });

  renderCart();
  showSkeletons();
  refreshIcons();
  loadProducts();
})();

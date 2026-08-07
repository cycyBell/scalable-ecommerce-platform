/**
 * ==============================================================================
 * MODULE: Shopping Cart REST API Controller (`src/controllers/cartController.js`)
 * ==============================================================================
 * 
 * WHAT DOES THIS CONTROLLER DO?
 * Acts as the HTTP interface layer connecting Express routes to `cartService`.
 * It extracts parameters, bodies, and authentication contexts (`req.cartKey`, `req.user`,
 * `req.guestId`), invokes business logic, and formats standardized HTTP responses.
 * ==============================================================================
 */

const cartService = require('../services/cartService');
const { BadRequestError } = require('../middleware/errorHandler');

/**
 * GET /cart
 * Purpose: Retrieves full enriched shopping cart details for current user or guest.
 */
async function getCart(req, res, next) {
    try {
        const cart = await cartService.getCart(req.cartKey);
        res.status(200).json(cart);
    } catch (err) {
        next(err);
    }
}

/**
 * POST /cart/items
 * Purpose: Adds a product item to the shopping cart after stock validation.
 * Body: { productId: string, quantity: number }
 */
async function addItem(req, res, next) {
    try {
        const { productId, quantity } = req.body;

        if (!productId || typeof productId !== 'string') {
            throw new BadRequestError("Request body must include a valid 'productId' string.");
        }

        const qty = parseInt(quantity, 10);
        if (isNaN(qty) || qty <= 0) {
            throw new BadRequestError("Request body must include a positive 'quantity' integer greater than 0.");
        }

        const updatedCart = await cartService.addItem(req.cartKey, productId, qty);
        res.status(201).json(updatedCart);
    } catch (err) {
        next(err);
    }
}

/**
 * PUT /cart/items/:productId
 * Purpose: Updates exact quantity for a product item in the shopping cart.
 * Body: { quantity: number }
 */
async function updateQuantity(req, res, next) {
    try {
        const { productId } = req.params;
        const { quantity } = req.body;

        if (!productId) {
            throw new BadRequestError("URL path parameter 'productId' is required.");
        }

        if (quantity === undefined || quantity === null) {
            throw new BadRequestError("Request body must include 'quantity'.");
        }

        const qty = parseInt(quantity, 10);
        if (isNaN(qty) || qty < 0) {
            throw new BadRequestError("'quantity' must be a non-negative integer (0 or greater).");
        }

        const updatedCart = await cartService.updateQuantity(req.cartKey, productId, qty);
        res.status(200).json(updatedCart);
    } catch (err) {
        next(err);
    }
}

/**
 * DELETE /cart/items/:productId
 * Purpose: Removes a single product item from the shopping cart.
 */
async function removeItem(req, res, next) {
    try {
        const { productId } = req.params;

        if (!productId) {
            throw new BadRequestError("URL path parameter 'productId' is required.");
        }

        const updatedCart = await cartService.removeItem(req.cartKey, productId);
        res.status(200).json(updatedCart);
    } catch (err) {
        next(err);
    }
}

/**
 * DELETE /cart
 * Purpose: Clears all items from the shopping cart (used after order checkout).
 */
async function clearCart(req, res, next) {
    try {
        const result = await cartService.clearCart(req.cartKey);
        res.status(200).json(result);
    } catch (err) {
        next(err);
    }
}

/**
 * POST /cart/merge
 * Purpose: Merges items from an anonymous guest cart into the authenticated user cart upon login.
 * Headers: Authorization: Bearer <token>
 * Body: { guestId?: string } or header X-Guest-Id
 */
async function mergeCart(req, res, next) {
    try {
        const guestId = req.body?.guestId || req.headers['x-guest-id'];

        if (!guestId || typeof guestId !== 'string' || guestId.trim().length === 0) {
            throw new BadRequestError("Guest ID is required in request body ('guestId') or 'X-Guest-Id' header to merge carts.");
        }

        const guestCartKey = `cart:guest:${guestId.trim()}`;
        const userCartKey = req.cartKey; // Computed as 'cart:' + req.user.userId by authMiddleware

        const mergedCart = await cartService.mergeCarts(guestCartKey, userCartKey);
        res.status(200).json(mergedCart);
    } catch (err) {
        next(err);
    }
}

module.exports = {
    getCart,
    addItem,
    updateQuantity,
    removeItem,
    clearCart,
    mergeCart
};

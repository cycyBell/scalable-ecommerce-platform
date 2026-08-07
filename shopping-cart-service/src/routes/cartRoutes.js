/**
 * ==============================================================================
 * MODULE: Shopping Cart Express Router (`src/routes/cartRoutes.js`)
 * ==============================================================================
 * 
 * ROUTE MAPPINGS:
 * - GET    /cart                 --> Fetch full enriched cart (Guest or Auth User)
 * - POST   /cart/items           --> Add product item to cart (with stock check)
 * - PUT    /cart/items/:productId--> Update item quantity (0 removes item)
 * - DELETE /cart/items/:productId--> Remove single product item
 * - DELETE /cart                 --> Clear entire cart (checkout cleanup)
 * - POST   /cart/merge           --> Merge guest cart on login (Requires Auth User)
 * ==============================================================================
 */

const express = require('express');
const router = express.Router();
const cartController = require('../controllers/cartController');
const { authenticate, requireAuth } = require('../middleware/authMiddleware');

// Mount authentication middleware on ALL cart routes to compute req.cartKey
router.use(authenticate);

// Public / Guest & Authenticated Customer Endpoints
router.get('/', cartController.getCart);
router.post('/items', cartController.addItem);
router.put('/items/:productId', cartController.updateQuantity);
router.delete('/items/:productId', cartController.removeItem);
router.delete('/', cartController.clearCart);

// Strictly Protected Endpoint (Requires logged-in user account)
router.post('/merge', requireAuth, cartController.mergeCart);

module.exports = router;

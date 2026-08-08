/**
 * ==============================================================================
 * UNIT TEST SUITE: Catalog Service Client (`tests/unit/catalogService.test.js`)
 * ==============================================================================
 * 
 * TESTS COVERED:
 * 1. validateProductStock: Validates product existence, active status, and inventory stock.
 * 2. validateProductStock Exceptions: Tests 404 Not Found, inactive status, and insufficient stock errors.
 * 3. Security Tests: Rejects path traversal and malformed product IDs.
 * 4. enrichCartItems: Tests subtotal calculations, total items sum, and missing product fallbacks.
 * ==============================================================================
 */

const { catalogClient, sanitizeAndValidateProductId, validateProductStock, enrichCartItems } = require('../../src/services/catalogService');
const { NotFoundError, BadRequestError } = require('../../src/middleware/errorHandler');

// Mock Axios catalogClient instance calls
jest.mock('../../src/services/catalogService', () => {
    const originalModule = jest.requireActual('../../src/services/catalogService');
    return {
        ...originalModule,
    };
});

describe('Catalog Service Client Unit Tests', () => {

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('sanitizeAndValidateProductId()', () => {
        test('should accept valid alphanumeric product IDs', () => {
            expect(sanitizeAndValidateProductId('prod_123-abc')).toBe('prod_123-abc');
            expect(sanitizeAndValidateProductId('65b4c9e82f1d9a0012345678')).toBe('65b4c9e82f1d9a0012345678');
        });

        test('should throw BadRequestError on path traversal characters', () => {
            expect(() => sanitizeAndValidateProductId('../admin/keys')).toThrow(BadRequestError);
            expect(() => sanitizeAndValidateProductId('..\\windows\\system32')).toThrow(BadRequestError);
            expect(() => sanitizeAndValidateProductId('/etc/passwd')).toThrow(BadRequestError);
        });

        test('should throw BadRequestError on special characters or spaces', () => {
            expect(() => sanitizeAndValidateProductId('prod 123')).toThrow(BadRequestError);
            expect(() => sanitizeAndValidateProductId('prod?admin=true')).toThrow(BadRequestError);
            expect(() => sanitizeAndValidateProductId('')).toThrow(BadRequestError);
        });
    });

    describe('validateProductStock()', () => {

        test('should return validated product details when stock is sufficient', async () => {
            jest.spyOn(catalogClient, 'get').mockResolvedValueOnce({
                data: {
                    id: 'prod-123',
                    title: 'Wireless Headphones',
                    price: 99.99,
                    is_active: true,
                    stock_quantity: 50,
                    image_url: 'https://example.com/headphones.jpg'
                }
            });

            const result = await validateProductStock('prod-123', 2);

            expect(catalogClient.get).toHaveBeenCalledWith('/products/prod-123');
            expect(result).toEqual({
                id: 'prod-123',
                title: 'Wireless Headphones',
                price: 99.99,
                stockQuantity: 50,
                imageUrl: 'https://example.com/headphones.jpg'
            });
        });

        test('should throw NotFoundError if product does not exist in catalog (404)', async () => {
            const error404 = new Error('Request failed with status code 404');
            error404.response = { status: 404 };
            jest.spyOn(catalogClient, 'get').mockRejectedValueOnce(error404);

            await expect(validateProductStock('non-existent-id', 1))
                .rejects.toThrow(NotFoundError);
        });

        test('should throw BadRequestError if product is inactive', async () => {
            jest.spyOn(catalogClient, 'get').mockResolvedValueOnce({
                data: {
                    id: 'prod-inactive',
                    title: 'Discontinued Keyboard',
                    price: 49.99,
                    is_active: false,
                    stock_quantity: 100
                }
            });

            await expect(validateProductStock('prod-inactive', 1))
                .rejects.toThrow(BadRequestError);
        });

        test('should throw BadRequestError if requested quantity exceeds available stock', async () => {
            jest.spyOn(catalogClient, 'get').mockResolvedValueOnce({
                data: {
                    id: 'prod-low-stock',
                    title: 'Limited Edition Watch',
                    price: 299.99,
                    is_active: true,
                    stock_quantity: 2
                }
            });

            await expect(validateProductStock('prod-low-stock', 5))
                .rejects.toThrow(BadRequestError);
        });

        test('should throw BadRequestError for invalid or non-positive quantity', async () => {
            await expect(validateProductStock('prod-123', 0))
                .rejects.toThrow(BadRequestError);

            await expect(validateProductStock('prod-123', -3))
                .rejects.toThrow(BadRequestError);
        });
    });

    describe('enrichCartItems()', () => {

        test('should return empty cart totals if cartHash is empty', async () => {
            const result = await enrichCartItems({});

            expect(result).toEqual({
                items: [],
                totalItems: 0,
                totalAmount: 0.00
            });
        });

        test('should enrich cart items and compute correct totals', async () => {
            jest.spyOn(catalogClient, 'get')
                .mockResolvedValueOnce({
                    data: {
                        id: 'prod-1',
                        title: 'Laptop Stand',
                        price: 25.50,
                        is_active: true,
                        stock_quantity: 20
                    }
                })
                .mockResolvedValueOnce({
                    data: {
                        id: 'prod-2',
                        title: 'USB-C Cable',
                        price: 10.00,
                        is_active: true,
                        stock_quantity: 100
                    }
                });

            const cartHash = {
                'prod-1': '2',
                'prod-2': '3'
            };

            const result = await enrichCartItems(cartHash);

            expect(result.totalItems).toBe(5);
            expect(result.totalAmount).toBe(81.00); // (25.50 * 2) + (10.00 * 3) = 51.00 + 30.00 = 81.00
            expect(result.items.length).toBe(2);

            expect(result.items[0]).toEqual({
                productId: 'prod-1',
                title: 'Laptop Stand',
                price: 25.50,
                quantity: 2,
                itemTotal: 51.00,
                imageUrl: null,
                isAvailable: true,
                availableStock: 20,
                availabilityError: null
            });
        });

        test('should handle deleted or missing catalog products gracefully without breaking subtotal calculations', async () => {
            const error404 = new Error('Not found');
            error404.response = { status: 404 };

            jest.spyOn(catalogClient, 'get')
                .mockResolvedValueOnce({
                    data: {
                        id: 'prod-1',
                        title: 'Valid Product',
                        price: 15.00,
                        is_active: true,
                        stock_quantity: 10
                    }
                })
                .mockRejectedValueOnce(error404);

            const cartHash = {
                'prod-1': '1',
                'prod-deleted': '2'
            };

            const result = await enrichCartItems(cartHash);

            expect(result.totalItems).toBe(3);
            expect(result.totalAmount).toBe(15.00);
            expect(result.items[1].isAvailable).toBe(false);
            expect(result.items[1].availabilityError).toBe('Product removed from catalog');
        });
    });
});

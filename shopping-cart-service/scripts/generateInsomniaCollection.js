/**
 * ==============================================================================
 * INSOMNIA REST API COLLECTION GENERATOR & LOCAL DATABASE SEEDER
 * ==============================================================================
 * 
 * Generates:
 * 1. An exportable Insomnia v4 JSON collection file (`insomnia_shopping_cart_collection.json`).
 * 2. Directly syncs/seeds the workspace, environment variables, folders, and request
 *    items into the installed Insomnia database (`%APPDATA%/Insomnia`).
 * ==============================================================================
 */

const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');

// Shared JWT Secret from platform configuration
const JWT_SECRET = '8d/vpFSCAFqeRdZD7W2ZbBUbvs9r3FajrfXlCDp4cTk=';

// Generate a valid 30-day development token for customer usr-demo-123
const demoToken = jwt.sign(
    {
        sub: 'usr-demo-123',
        roles: ['ROLE_CUSTOMER'],
        email: 'customer@scalable-ecommerce.com'
    },
    JWT_SECRET,
    { expiresIn: '30d' }
);

const now = Date.now();
const workspaceId = 'wrk_cart_service_v2';
const environmentId = 'env_cart_service_v2';
const folderId = 'fld_cart_endpoints_v2';
const workspaceMetaId = 'wrkm_cart_service_v2';

// Real MongoDB Product IDs currently active in Product Catalog Service
const REAL_PRODUCT_ID_1 = '6a6ea1c4d4e9d1e69393a95d'; // Wireless Ergonomic Mouse ($29.99, stock: 50)
const REAL_PRODUCT_ID_2 = '6a71246314bf2f072eb063de'; // Pro Gaming Laptop ($1199.99, stock: 15)

// ------------------------------------------------------------------------------
// Insomnia Export Format (v4)
// ------------------------------------------------------------------------------
const insomniaExportData = {
    _type: 'export',
    __export_format: 4,
    __export_date: new Date().toISOString(),
    __export_source: 'insomnia.desktop.app:v10.3.0',
    resources: [
        // 1. Workspace
        {
            _id: workspaceId,
            parentId: null,
            modified: now,
            created: now,
            name: 'Shopping Cart Microservice',
            description: 'Production REST API collection for the Node.js + Redis Shopping Cart Service.',
            scope: 'collection',
            _type: 'workspace'
        },
        // 2. Base Environment
        {
            _id: environmentId,
            parentId: workspaceId,
            modified: now,
            created: now,
            name: 'Local / Docker Environment',
            data: {
                base_url: 'http://localhost:8002',
                guest_id: 'guest_demo_778899',
                product_id: REAL_PRODUCT_ID_1,
                product_id_2: REAL_PRODUCT_ID_2,
                jwt_token: demoToken
            },
            dataPropertyOrder: {
                '&': ['base_url', 'guest_id', 'product_id', 'product_id_2', 'jwt_token']
            },
            color: '#7d69cb',
            isPrivate: false,
            metaSortKey: now,
            environmentType: 'kv',
            _type: 'environment'
        },
        // 3. Request Group / Folder
        {
            _id: folderId,
            parentId: workspaceId,
            modified: now,
            created: now,
            name: 'Cart Operations & Health',
            description: 'Core cart endpoints supporting guest sessions, JWT authentication, stock validation, and Redis atomic operations.',
            environment: {},
            environmentPropertyOrder: null,
            metaSortKey: -now,
            _type: 'request_group'
        },
        // 4. Request: Health Check
        {
            _id: 'req_cart_health',
            parentId: folderId,
            modified: now,
            created: now,
            url: '{{ _.base_url }}/health',
            name: '1. Health Check Probe (Service & Redis)',
            description: 'Returns real-time service health, uptime, and active Redis PING latency.',
            method: 'GET',
            body: {},
            parameters: [],
            headers: [],
            authentication: {},
            metaSortKey: 100,
            isPrivate: false,
            settingStoreCookies: true,
            settingSendCookies: true,
            settingDisableRenderRequestBody: false,
            settingEncodeUrl: true,
            settingRebuildPath: true,
            settingFollowRedirects: 'global',
            _type: 'request'
        },
        // 5. Request: Get Cart (Guest)
        {
            _id: 'req_cart_get_guest',
            parentId: folderId,
            modified: now,
            created: now,
            url: '{{ _.base_url }}/cart',
            name: '2. Get Cart (Guest Session)',
            description: 'Fetches full enriched shopping cart with live catalog prices and stock availability using X-Guest-Id.',
            method: 'GET',
            body: {},
            parameters: [],
            headers: [
                {
                    name: 'X-Guest-Id',
                    value: '{{ _.guest_id }}',
                    description: 'Anonymous visitor session identifier'
                }
            ],
            authentication: {},
            metaSortKey: 200,
            isPrivate: false,
            settingStoreCookies: true,
            settingSendCookies: true,
            settingDisableRenderRequestBody: false,
            settingEncodeUrl: true,
            settingRebuildPath: true,
            settingFollowRedirects: 'global',
            _type: 'request'
        },
        // 6. Request: Get Cart (Authenticated User)
        {
            _id: 'req_cart_get_user',
            parentId: folderId,
            modified: now,
            created: now,
            url: '{{ _.base_url }}/cart',
            name: '3. Get Cart (Authenticated Customer)',
            description: 'Fetches permanent shopping cart for a logged-in user verified via Zero-DB HMAC JWT signature.',
            method: 'GET',
            body: {},
            parameters: [],
            headers: [
                {
                    name: 'Authorization',
                    value: 'Bearer {{ _.jwt_token }}',
                    description: 'Customer JWT Bearer Access Token'
                }
            ],
            authentication: {},
            metaSortKey: 300,
            isPrivate: false,
            settingStoreCookies: true,
            settingSendCookies: true,
            settingDisableRenderRequestBody: false,
            settingEncodeUrl: true,
            settingRebuildPath: true,
            settingFollowRedirects: 'global',
            _type: 'request'
        },
        // 7. Request: Add Item to Cart (Guest)
        {
            _id: 'req_cart_add_item_guest',
            parentId: folderId,
            modified: now,
            created: now,
            url: '{{ _.base_url }}/cart/items',
            name: '4. Add Item to Cart (Guest)',
            description: 'Validates stock against Product Catalog Service and atomics-adds item into Redis hash with 7-day TTL.',
            method: 'POST',
            body: {
                mimeType: 'application/json',
                text: JSON.stringify(
                    {
                        productId: '{{ _.product_id }}',
                        quantity: 2
                    },
                    null,
                    2
                )
            },
            parameters: [],
            headers: [
                {
                    name: 'Content-Type',
                    value: 'application/json'
                },
                {
                    name: 'X-Guest-Id',
                    value: '{{ _.guest_id }}'
                }
            ],
            authentication: {},
            metaSortKey: 400,
            isPrivate: false,
            settingStoreCookies: true,
            settingSendCookies: true,
            settingDisableRenderRequestBody: false,
            settingEncodeUrl: true,
            settingRebuildPath: true,
            settingFollowRedirects: 'global',
            _type: 'request'
        },
        // 8. Request: Add Item to Cart (Authenticated User)
        {
            _id: 'req_cart_add_item_user',
            parentId: folderId,
            modified: now,
            created: now,
            url: '{{ _.base_url }}/cart/items',
            name: '5. Add Item to Cart (Customer Account)',
            description: 'Adds item directly into authenticated user shopping cart.',
            method: 'POST',
            body: {
                mimeType: 'application/json',
                text: JSON.stringify(
                    {
                        productId: '{{ _.product_id_2 }}',
                        quantity: 1
                    },
                    null,
                    2
                )
            },
            parameters: [],
            headers: [
                {
                    name: 'Content-Type',
                    value: 'application/json'
                },
                {
                    name: 'Authorization',
                    value: 'Bearer {{ _.jwt_token }}'
                }
            ],
            authentication: {},
            metaSortKey: 500,
            isPrivate: false,
            settingStoreCookies: true,
            settingSendCookies: true,
            settingDisableRenderRequestBody: false,
            settingEncodeUrl: true,
            settingRebuildPath: true,
            settingFollowRedirects: 'global',
            _type: 'request'
        },
        // 9. Request: Update Item Quantity
        {
            _id: 'req_cart_update_quantity',
            parentId: folderId,
            modified: now,
            created: now,
            url: '{{ _.base_url }}/cart/items/{{ _.product_id }}',
            name: '6. Update Item Quantity in Cart',
            description: 'Updates product quantity (validates stock) or removes item if quantity is set to 0.',
            method: 'PUT',
            body: {
                mimeType: 'application/json',
                text: JSON.stringify(
                    {
                        quantity: 4
                    },
                    null,
                    2
                )
            },
            parameters: [],
            headers: [
                {
                    name: 'Content-Type',
                    value: 'application/json'
                },
                {
                    name: 'X-Guest-Id',
                    value: '{{ _.guest_id }}'
                }
            ],
            authentication: {},
            metaSortKey: 600,
            isPrivate: false,
            settingStoreCookies: true,
            settingSendCookies: true,
            settingDisableRenderRequestBody: false,
            settingEncodeUrl: true,
            settingRebuildPath: true,
            settingFollowRedirects: 'global',
            _type: 'request'
        },
        // 10. Request: Remove Item
        {
            _id: 'req_cart_remove_item',
            parentId: folderId,
            modified: now,
            created: now,
            url: '{{ _.base_url }}/cart/items/{{ _.product_id }}',
            name: '7. Remove Single Item from Cart',
            description: 'Removes the specified product from Redis hash via HDEL command.',
            method: 'DELETE',
            body: {},
            parameters: [],
            headers: [
                {
                    name: 'X-Guest-Id',
                    value: '{{ _.guest_id }}'
                }
            ],
            authentication: {},
            metaSortKey: 700,
            isPrivate: false,
            settingStoreCookies: true,
            settingSendCookies: true,
            settingDisableRenderRequestBody: false,
            settingEncodeUrl: true,
            settingRebuildPath: true,
            settingFollowRedirects: 'global',
            _type: 'request'
        },
        // 11. Request: Clear Cart
        {
            _id: 'req_cart_clear',
            parentId: folderId,
            modified: now,
            created: now,
            url: '{{ _.base_url }}/cart',
            name: '8. Clear Entire Cart (Checkout Cleanup)',
            description: 'Purges entire cart from Redis via DEL key on checkout completion.',
            method: 'DELETE',
            body: {},
            parameters: [],
            headers: [
                {
                    name: 'X-Guest-Id',
                    value: '{{ _.guest_id }}'
                }
            ],
            authentication: {},
            metaSortKey: 800,
            isPrivate: false,
            settingStoreCookies: true,
            settingSendCookies: true,
            settingDisableRenderRequestBody: false,
            settingEncodeUrl: true,
            settingRebuildPath: true,
            settingFollowRedirects: 'global',
            _type: 'request'
        },
        // 12. Request: Merge Guest Cart
        {
            _id: 'req_cart_merge',
            parentId: folderId,
            modified: now,
            created: now,
            url: '{{ _.base_url }}/cart/merge',
            name: '9. Merge Guest Cart on Customer Login',
            description: 'Atomically merges anonymous guest cart items into authenticated customer cart via Redis pipeline.',
            method: 'POST',
            body: {
                mimeType: 'application/json',
                text: JSON.stringify(
                    {
                        guestId: '{{ _.guest_id }}'
                    },
                    null,
                    2
                )
            },
            parameters: [],
            headers: [
                {
                    name: 'Content-Type',
                    value: 'application/json'
                },
                {
                    name: 'Authorization',
                    value: 'Bearer {{ _.jwt_token }}'
                }
            ],
            authentication: {},
            metaSortKey: 900,
            isPrivate: false,
            settingStoreCookies: true,
            settingSendCookies: true,
            settingDisableRenderRequestBody: false,
            settingEncodeUrl: true,
            settingRebuildPath: true,
            settingFollowRedirects: 'global',
            _type: 'request'
        }
    ]
};

// ------------------------------------------------------------------------------
// Step 1: Write standalone export JSON in project repository
// ------------------------------------------------------------------------------
const outputPath = path.join(__dirname, '..', 'insomnia_shopping_cart_collection.json');
fs.writeFileSync(outputPath, JSON.stringify(insomniaExportData, null, 2), 'utf8');
console.log(`[SUCCESS] Generated standalone Insomnia Collection JSON at: ${outputPath}`);

// Also copy to root for quick access
const rootPath = path.join(__dirname, '..', '..', 'insomnia_cart_service_collection.json');
fs.writeFileSync(rootPath, JSON.stringify(insomniaExportData, null, 2), 'utf8');

// ------------------------------------------------------------------------------
// Step 2: Seed directly into local Insomnia Database (%APPDATA%/Insomnia)
// ------------------------------------------------------------------------------
const appData = process.env.APPDATA || (process.platform === 'darwin' ? path.join(process.env.HOME, 'Library', 'Application Support') : path.join(process.env.HOME, '.config'));
const insomniaDir = path.join(appData, 'Insomnia');

if (fs.existsSync(insomniaDir)) {
    console.log(`[INFO] Seeding collection directly into Insomnia data store at: ${insomniaDir}`);

    const appendDoc = (filename, doc) => {
        const filePath = path.join(insomniaDir, filename);
        const line = JSON.stringify(doc) + '\n';
        fs.appendFileSync(filePath, line, 'utf8');
    };

    // 1. Workspace
    appendDoc('insomnia.Workspace.db', {
        _id: workspaceId,
        type: 'Workspace',
        parentId: 'proj_2a7ef69494e14a40932bbd007f662a1e',
        modified: now,
        created: now,
        name: 'Shopping Cart Microservice',
        description: 'Complete production REST API collection for the Shopping Cart Service',
        scope: 'collection'
    });

    // 2. WorkspaceMeta
    appendDoc('insomnia.WorkspaceMeta.db', {
        _id: workspaceMetaId,
        type: 'WorkspaceMeta',
        parentId: workspaceId,
        modified: now,
        created: now,
        activeActivity: null,
        activeEnvironmentId: environmentId,
        activeGlobalEnvironmentId: null,
        activeRequestId: 'req_cart_health',
        activeUnitTestSuiteId: null,
        gitRepositoryId: null,
        gitFilePath: null,
        gitFileLastSyncTime: null,
        pushSnapshotOnInitialize: false,
        hasUncommittedChanges: false,
        hasUnpushedChanges: false
    });

    // 3. Environment
    appendDoc('insomnia.Environment.db', {
        _id: environmentId,
        type: 'Environment',
        parentId: workspaceId,
        modified: now,
        created: now,
        name: 'Base Environment',
        data: {
            base_url: 'http://localhost:8002',
            guest_id: 'guest_demo_778899',
            product_id: REAL_PRODUCT_ID_1,
            product_id_2: REAL_PRODUCT_ID_2,
            jwt_token: demoToken
        },
        dataPropertyOrder: {
            '&': ['base_url', 'guest_id', 'product_id', 'product_id_2', 'jwt_token']
        },
        color: '#7d69cb',
        isPrivate: false,
        metaSortKey: now,
        environmentType: 'kv'
    });

    // 4. Request Group / Folder
    appendDoc('insomnia.RequestGroup.db', {
        _id: folderId,
        type: 'RequestGroup',
        parentId: workspaceId,
        modified: now,
        created: now,
        name: 'Cart Operations & Health',
        description: 'Core cart endpoints supporting guest sessions, JWT authentication, stock validation, and Redis atomic operations.',
        environment: {},
        environmentPropertyOrder: null,
        metaSortKey: -now
    });

    // 5. Request Documents
    const requests = insomniaExportData.resources.filter(r => r._type === 'request');
    for (const req of requests) {
        const reqDoc = {
            _id: req._id,
            type: 'Request',
            parentId: req.parentId,
            modified: req.modified,
            created: req.created,
            url: req.url,
            name: req.name,
            description: req.description,
            method: req.method,
            body: req.body,
            parameters: req.parameters,
            headers: req.headers,
            authentication: req.authentication,
            metaSortKey: req.metaSortKey,
            isPrivate: req.isPrivate,
            settingStoreCookies: req.settingStoreCookies,
            settingSendCookies: req.settingSendCookies,
            settingDisableRenderRequestBody: req.settingDisableRenderRequestBody,
            settingEncodeUrl: req.settingEncodeUrl,
            settingRebuildPath: req.settingRebuildPath,
            settingFollowRedirects: req.settingFollowRedirects
        };
        appendDoc('insomnia.Request.db', reqDoc);
    }

    console.log(`[SUCCESS] Seeded ${requests.length} requests and active environment with real MongoDB product IDs.`);
}

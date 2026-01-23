package com.example.auctionhub.auctionhub.constants;

public class ApiConstants {
    
    // Base API path
    public static final String API_BASE = "/api";
    
    // Auth endpoints
    public static final String AUTH_BASE = API_BASE + "/auth";
    public static final String AUTH_REGISTER = "/register";
    public static final String AUTH_LOGIN = "/login";
    public static final String AUTH_LOGOUT = "/logout";
    public static final String AUTH_REFRESH = "/refresh";
    public static final String AUTH_CHECK_USERNAME = "/check-username/{username}";
    public static final String AUTH_CHECK_EMAIL = "/check-email/{email}";

    
    // Items endpoints
    public static final String ITEM_BASE = API_BASE + "/items";
    public static final String ITEM_CREATE = ""; // POST /api/items
    public static final String ITEM_EDIT = ITEM_BASE + "/{itemId}"; // PUT /api/items/{itemId}
    public static final String ITEM_REPLACE_IMAGE = ITEM_BASE + "/{itemId}/images/{imageId}"; // PUT /api/items/{itemId}/images/{imageId}
    public static final String ITEM_REMOVE_IMAGE = ITEM_BASE + "/{itemId}/images/{imageId}"; // DELETE /api/items/{itemId}/images/{imageId}
    public static final String ITEM_SWAP_IMAGE_INDEX = ITEM_BASE + "/{itemId}/images/swap"; // POST /api/items/{itemId}/images/swap

    // Payment endpoints
    public static final String PAYMENT_BASE = API_BASE + "/payments";
    public static final String PAYMENT_CREATE = ""; // POST /api/payments
    public static final String PAYMENT_HISTORY = "/history"; // GET /api/payments/history
    public static final String PAYMENT_GET_BY_ID = "/{paymentId}"; // GET /api/payments/{paymentId}
    public static final String PAYMENT_CANCEL = "/{paymentId}/cancel"; // POST /api/payments/{paymentId}/cancel
    public static final String PAYMENT_REFUND = "/{paymentId}/refund"; // POST /api/payments/{paymentId}/refund

    // Private constructor to prevent instantiation
    private ApiConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}

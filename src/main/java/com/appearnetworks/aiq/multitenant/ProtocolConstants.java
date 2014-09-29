package com.appearnetworks.aiq.multitenant;

public final class ProtocolConstants {
    // Standard HTTP headers
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String IF_MATCH = "If-Match";

    // Custom HTTP headers
    public static final String X_AIQ_USER_ID = "X-AIQ-UserId";
    public static final String X_AIQ_DEVICE_ID = "X-AIQ-DeviceId";
    public static final String X_AIQ_MESSAGE_ID = "X-AIQ-MessageId";
    public static final String X_AIQ_CREATED = "X-AIQ-Created";

    // Document types
    public static final String CLIENT_SESSION_DOC_TYPE = "_clientsession";

    private ProtocolConstants() { }
}

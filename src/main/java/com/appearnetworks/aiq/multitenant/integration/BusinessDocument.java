package com.appearnetworks.aiq.multitenant.integration;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Represent a business document in data sync.
 *
 * All document to be used with the data sync system must have _id, _type and _rev fields.
 *
 * Document id and type and attachment names can only contain characters {@code a-zA-Z0-9.~-_} (corresponds to the unreserved characters of an URI
 * according to <a href="http://tools.ietf.org/html/rfc3986#section-2.3">RFC-3986</a>) and must be between 1 and 250 characters long.
 */
public abstract class BusinessDocument {
    protected String _id;
    protected String _type;
    protected long _rev;

    protected Map<String,AttachmentReference> _attachments;

    /**
     * Needed for Jackson deserialization, do not use.
     */
    public BusinessDocument() {}

    /**
     * Document without attachments.
     *
     * @param _id    document id
     * @param _type  document type
     * @param _rev   document revision
     */
    public BusinessDocument(String _id, String _type, long _rev) {
        this(_id, _type, _rev, null);
    }

    /**
     * Document with attachments.
     *
     * @param _id           document id
     * @param _type         document type
     * @param _rev          document revision
     * @param _attachments  attachments, keys are attachment names
     */
    public BusinessDocument(String _id, String _type, long _rev, Map<String,AttachmentReference> _attachments) {
        validateId(_id, "document id");
        validateId(_type, "document type");
        if (_attachments != null) {
            for (Map.Entry<String,AttachmentReference> attachment : _attachments.entrySet()) {
                validateId(attachment.getKey(), "attachment name");
            }
        }
        this._id = _id;
        this._type = _type;
        this._rev = _rev;
        this._attachments = _attachments;
    }

    public String get_id() {
        return _id;
    }

    public String get_type() {
        return _type;
    }

    public long get_rev() {
        return _rev;
    }

    @JsonSerialize(include= JsonSerialize.Inclusion.NON_NULL)
    public Map<String, AttachmentReference> get_attachments() {
        return _attachments;
    }

    private static final Pattern ID_REGEX = Pattern.compile("[a-zA-Z0-9.~_-]+");

    private static void validateId(String id, String name) {
        if (!ID_REGEX.matcher(id).matches() || id.length() > 250)
            throw new IllegalArgumentException("Invalid " + name + ": " + id);
    }
}

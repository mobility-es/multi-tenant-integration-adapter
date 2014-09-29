package com.appearnetworks.aiq.multitenant.integration;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
public class BusinessDocument {
    private String _id;
    private String _type;
    private long _rev;
    private ObjectNode body;

    protected Map<String,AttachmentReference> _attachments;

    /**
     * Needed for Jackson deserialization, do not use.
     */
    public BusinessDocument() {}

    public BusinessDocument(String _id, String _type, long _rev, ObjectNode body) {
        this(_id, _type, _rev, null, body);
    }

    /**
     * Document without attachments.
     *
     * @param _id    document id
     * @param _type  document type
     * @param _rev   document revision
     */
    public BusinessDocument(String _id, String _type, long _rev) {
        this(_id, _type, _rev, null, null);
    }

    /**
     * Document with attachments.
     *
     * @param _id           document id
     * @param _type         document type
     * @param _rev          document revision
     * @param _attachments  attachments, keys are attachment names
     */
    public BusinessDocument(String _id, String _type, long _rev, Map<String,AttachmentReference> _attachments, ObjectNode body) {
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
        this.body = body;
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

    public ObjectNode getBody() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BusinessDocument that = (BusinessDocument) o;

        return _rev == that._rev &&
               !(_id != null ? !_id.equals(that._id) : that._id != null) &&
               !(_type != null ? !_type.equals(that._type) : that._type != null);

    }

    @Override
    public int hashCode() {
        int result = _id != null ? _id.hashCode() : 0;
        result = 31 * result + (_type != null ? _type.hashCode() : 0);
        result = 31 * result + (int) (_rev ^ (_rev >>> 32));
        return result;
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

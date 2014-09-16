package com.appearnetworks.aiq.multitenant.impl.integration;

import com.appearnetworks.aiq.multitenant.integration.BusinessDocument;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Document extends BusinessDocument {

    private final ObjectNode body;

    public Document(String _id, String _type, long _rev, ObjectNode body) {
        super(_id, _type, _rev);
        this.body = body;
    }

    public ObjectNode getBody() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Document that = (Document) o;

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

}

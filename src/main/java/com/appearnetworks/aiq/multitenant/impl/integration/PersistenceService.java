package com.appearnetworks.aiq.multitenant.impl.integration;

import com.appearnetworks.aiq.multitenant.integration.DocumentReference;
import com.appearnetworks.aiq.multitenant.integration.UpdateException;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;

public interface PersistenceService {

    Collection<DocumentReference> list(String orgName);

    ObjectNode retrieve(String orgName, String docId);

    long insert(String orgName, DocumentReference docRef, ObjectNode doc) throws UpdateException;

    long update(String orgName, DocumentReference docRef, ObjectNode doc) throws UpdateException;

    void delete(String orgName, DocumentReference docRef) throws UpdateException;

}

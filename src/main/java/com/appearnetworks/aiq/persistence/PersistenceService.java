package com.appearnetworks.aiq.persistence;

import com.appearnetworks.aiq.integrationframework.integration.DocumentReference;
import com.appearnetworks.aiq.integrationframework.integration.UpdateException;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;

interface PersistenceService {

    Collection<DocumentReference> list();

    ObjectNode retrieve(String docId);

    long insert(DocumentReference docRef, ObjectNode doc) throws UpdateException;

    long update(DocumentReference docRef, ObjectNode doc) throws UpdateException;

    void delete(DocumentReference docRef) throws UpdateException;

}

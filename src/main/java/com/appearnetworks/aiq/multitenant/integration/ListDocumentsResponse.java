package com.appearnetworks.aiq.multitenant.integration;

import java.util.Collection;

public final class ListDocumentsResponse {
  public final Collection<? extends DocumentReference> documentReferences;

  public ListDocumentsResponse(Collection<? extends DocumentReference> documentReferences) {
    this.documentReferences = documentReferences;
  }
}

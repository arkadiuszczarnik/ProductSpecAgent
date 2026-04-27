package com.agentwork.productspecagent.service

class DocumentNotFoundException(documentId: String) : RuntimeException("Document not found: $documentId")

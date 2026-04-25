package com.agentwork.productspecagent.service

class UnsupportedMediaTypeException(actualMime: String) :
    RuntimeException("Unsupported MIME type: $actualMime. Allowed: application/pdf, text/markdown, text/plain")

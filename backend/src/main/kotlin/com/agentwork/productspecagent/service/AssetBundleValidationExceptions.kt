package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType

class MissingManifestException(message: String) : RuntimeException(message)

class InvalidManifestException(message: String) : RuntimeException(message)

class ManifestIdMismatchException(expected: String, actual: String) :
    RuntimeException("Manifest id mismatch: expected '$expected', got '$actual'")

class UnsupportedStepException(step: FlowStepType) :
    RuntimeException("Bundles only supported for BACKEND, FRONTEND, ARCHITECTURE — got $step")

class IllegalBundleEntryException(path: String, reason: String) :
    RuntimeException("Illegal bundle entry '$path': $reason")

class BundleTooLargeException(message: String) : RuntimeException(message)

class BundleFileNotFoundException(bundleId: String, relativePath: String) :
    RuntimeException("File not found in bundle '$bundleId': $relativePath")

package com.agentwork.productspecagent.service

class AssetBundleNotFoundException(id: String) : RuntimeException("Asset bundle not found: $id")

package com.agentwork.productspecagent.infrastructure.graphmesh

sealed class GraphMeshException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class Unavailable(cause: Throwable) : GraphMeshException("GraphMesh is not reachable", cause)
    class GraphQlError(val detail: String) : GraphMeshException("GraphMesh returned errors: $detail")
}

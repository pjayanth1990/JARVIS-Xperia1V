package com.jps.jarvis.core

class CapabilityRegistry(descriptors: Collection<CapabilityDescriptor>) {
    private val byId: Map<String, CapabilityDescriptor>
    init {
        require(descriptors.map { it.id }.distinct().size == descriptors.size) { "Duplicate capability IDs" }
        require(descriptors.all { it.id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+")) }) { "Invalid capability ID" }
        byId = descriptors.associateBy { it.id }
    }
    fun find(id: String): CapabilityDescriptor? = byId[id]
    fun advertisedAvailableIds(): Set<String> = byId.values.filter { it.available }.map { it.id }.toSet()
    fun all(): Collection<CapabilityDescriptor> = byId.values
    companion object { fun initial() = CapabilityRegistry(InitialCapabilities.all) }
}

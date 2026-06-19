package com.ulysses.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A BlockList is a collection of rules (websites or apps) that can be
 * either an Allowlist or a Denylist.
 */
@Entity(tableName = "block_lists")
data class BlockListEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * An entry within a BlockList. Can be a website rule or an app rule.
 */
@Entity(tableName = "block_list_entries")
data class BlockListEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String, // FK to BlockListEntity
    val type: String = "deny", // "deny" or "allow"
    val ruleType: String = "domain", // "domain", "url", "wildcard", "app_name", "window_title"
    val value: String, // domain, url pattern, package name, keyword, or category name
    val isActive: Boolean = true
)

/**
 * A Block is a template bundling lists and a default locking method.
 */
@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val defaultLockType: String = "timer", // "timer" or "password"
    val defaultLockValue: String = "3600000", // millis for timer, hash for password
    val iconEmoji: String = "🛡️",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Junction table linking Blocks to BlockLists.
 */
@Entity(tableName = "block_list_associations", primaryKeys = ["blockId", "listId"])
data class BlockListAssociation(
    val blockId: String,
    val listId: String
)

/**
 * An active block session.
 */
@Entity(tableName = "block_sessions")
data class BlockSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val blockId: String,
    val triggerId: String? = null,
    val lockType: String, // "timer" or "password"
    val lockValue: String, // expiry timestamp (millis) for timer, hash for password
    val startedAt: Long = System.currentTimeMillis(),
    val endsAt: Long? = null, // null for password-locked (indefinite until password)
    val isActive: Boolean = true,
    val propagateToNetwork: Boolean = false
)

/**
 * A trigger that can fire a block session.
 */
@Entity(tableName = "triggers")
data class TriggerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val blockId: String, // FK to BlockEntity
    val isManual: Boolean = true,
    val propagateToNetwork: Boolean = false,
    val lamportTimestamp: Long = 1,
    val isDeleted: Boolean = false, // tombstone flag
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * A DNS blocklist category (e.g., "Social Media", "Adult", "Gambling").
 */
@Entity(tableName = "dns_categories")
data class DnsCategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String, // "Social Media", "Adult Content", etc.
    val description: String = "",
    val domainCount: Int = 0,
    val sourceUrl: String = "", // URL of the blocklist source
    val lastUpdated: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)

/**
 * Individual domain entries for DNS category blocking.
 */
@Entity(tableName = "dns_blocked_domains")
data class DnsBlockedDomainEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val categoryId: String, // FK to DnsCategoryEntity
    val domain: String
)
